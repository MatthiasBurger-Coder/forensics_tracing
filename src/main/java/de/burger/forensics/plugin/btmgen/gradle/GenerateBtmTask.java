package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.adapters.javaparser.JavaParserScanner;
import de.burger.forensics.application.service.GenerateRulesUseCase;
import de.burger.forensics.application.service.GenerationRequest;
import de.burger.forensics.application.service.RuleGenerationResult;
import de.burger.forensics.domain.strategy.DefaultStrategyFactory;
import de.burger.forensics.plugin.adapters.GradleLogAdapter;
import de.burger.forensics.plugin.adapters.SystemClockAdapter;
import de.burger.forensics.plugin.btmgen.internal.BytemanRuleRenderAdapter;
import de.burger.forensics.plugin.btmgen.render.BytemanRuleRenderer;
import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import de.burger.forensics.plugin.btmgen.writer.BtmFileWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Scans Java sources and renders Byteman rules using {@link GenerateRulesUseCase}.
 */
public abstract class GenerateBtmTask extends DefaultTask {
    // ---- Configurable inputs ----
    @Internal
    public abstract DirectoryProperty getSourceRoot();

    @Internal
    public abstract ConfigurableFileCollection getSourceRoots();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSourceFiles() {
        applyDefaultConventions();
        return sourceFilesFor(resolveSourceRoots());
    }

    @OutputFile
    @Optional
    public abstract RegularFileProperty getOutputFile();

    @Internal
    public abstract DirectoryProperty getOutputDir();

    /** Optional: render exactly one template with given class/method instead of scanning. */
    @Input @Optional public abstract Property<@NotNull String> getTemplateId();
    @Input @Optional public abstract Property<@NotNull String> getClassName();
    @Input @Optional public abstract Property<@NotNull String> getMethodName();
    @Input @Optional public abstract Property<@NotNull String> getMethodDesc();
    @Input @Optional public abstract Property<@NotNull Boolean> getIncludeEntryExit();
    @Input @Optional public abstract Property<@NotNull Integer>  getMinBranchesPerMethod();
    @Input @Optional public abstract Property<@NotNull String>  getHelperFqn();
    @Input @Optional public abstract Property<@NotNull Boolean> getScanSubprojects();

    // Provided by plugin (or set manually in build script)
    private BtmGenExtension extension;

    /** Injected via plugin apply() */
    public void setExtension(BtmGenExtension ext) {
        this.extension = Objects.requireNonNull(ext, "BtmGenExtension must not be null");

        applyDefaultConventions();

        if (ext.getSourceRoot().isPresent()) {
            getSourceRoot().set(ext.getSourceRoot().get());
        }
        getSourceRoots().setFrom(ext.getSourceRoots());
        if (ext.getOutputFile().isPresent()) {
            var file = ext.getOutputFile().get();
            getOutputFile().fileValue(file);
            if (file.getParentFile() != null) {
                getOutputDir().fileValue(file.getParentFile());
            }
        }
        getHelperFqn().convention(ext.getHelperFqn());
        getMinBranchesPerMethod().convention(ext.getMinBranchesPerMethod());
        getScanSubprojects().convention(ext.getScanSubprojects());
    }

    @TaskAction
    public void generate() {
        ensureExtension();
        applyDefaultConventions();
        final Path outFile = getOutputFile().get().getAsFile().toPath();
        final StrategyRegistry registry = extension.getRegistry() != null
                ? extension.getRegistry() : StrategyRegistries.defaultRegistry();

        BytemanRuleRenderer renderer = BytemanRuleRenderer.of(registry);

        try {
            Files.createDirectories(outFile.getParent());
        } catch (IOException e) {
            throw new GradleException("Failed to create output directory for " + outFile, e);
        }

        List<String> allRules = new ArrayList<>();

        if (hasMinimalInputs()) {
            // Single-template mode (explicit params)
            RuleParams params = new RuleParams(
                    templateIdOrDefault(),
                    getClassName().get(),
                    getMethodName().get(),
                    getMethodDesc().getOrNull(),
                    getClassName().get() + "#" + getMethodName().get(),
                    null,
                    null,
                    resolveHelperFqn()
            );
            allRules.add(renderer.render(templateIdOrDefault(), params));
        } else {
            List<Path> roots = resolveSourceRoots();
            GenerateRulesUseCase useCase = new GenerateRulesUseCase(
                new JavaParserScanner(),
                new BytemanRuleRenderAdapter(renderer),
                new SystemClockAdapter(),
                new GradleLogAdapter(getLogger()),
                new DefaultStrategyFactory()
            );
            for (Path srcRoot : roots) {
                getLogger().lifecycle("Scanning sources in {}", srcRoot.toAbsolutePath());
                GenerationRequest request = new GenerationRequest(
                    srcRoot,
                    resolveHelperFqn(),
                    false,
                    includeEntryExit(),
                    packagePrefixes(),
                    minBranches(),
                    Collections.emptyList()
                );
                RuleGenerationResult result = useCase.generate(request);
                allRules.addAll(result.renderedRules());
            }
        }

        List<String> uniqueRules = new ArrayList<>(new LinkedHashSet<>(allRules));
        List<String> dedupedRuleNames = dedupeRuleHeaders(uniqueRules);

        // Write output once
        writeRules(outFile, dedupedRuleNames);

        getLogger().lifecycle("Generated {} rules -> {}", dedupedRuleNames.size(), outFile.toAbsolutePath());
    }

    private void ensureExtension() {
        if (this.extension == null) {
            BtmGenExtension ext = getProject().getExtensions().findByType(BtmGenExtension.class);
            if (ext == null) {
                ext = getProject().getObjects().newInstance(BtmGenExtension.class);
            }
            setExtension(ext);
        }
    }

    private void applyDefaultConventions() {
        ProjectLayout layout = getProject().getLayout();
        if (!getSourceRoot().isPresent()) {
            getSourceRoot().convention(layout.getProjectDirectory().dir("src/main/java"));
        }
        if (!getOutputFile().isPresent()) {
            getOutputFile().convention(
                    layout.getBuildDirectory().file("forensics/forensics.btm")
            );
        }
        if (!getOutputDir().isPresent()) {
            getOutputDir().convention(
                    layout.getBuildDirectory().dir("forensics")
            );
        }
        if (!getIncludeEntryExit().isPresent()) {
            getIncludeEntryExit().convention(true);
        }
        if (!getMinBranchesPerMethod().isPresent()) {
            getMinBranchesPerMethod().convention(2);
        }
        if (!getScanSubprojects().isPresent()) {
            getScanSubprojects().convention(false);
        }
    }

    private boolean hasMinimalInputs() {
        return getTemplateId().isPresent() && getClassName().isPresent() && getMethodName().isPresent();
    }

    private String templateIdOrDefault() {
        String id = getTemplateId().getOrElse("METHOD_ENTER");
        return id.isBlank() ? "METHOD_ENTER" : id;
    }

    private String resolveHelperFqn() {
        String helper = getHelperFqn().getOrElse(RuleParams.DEFAULT_HELPER_FQN);
        return helper.isBlank() ? RuleParams.DEFAULT_HELPER_FQN : helper;
    }

    private boolean includeEntryExit() {
        return getIncludeEntryExit().getOrElse(true);
    }

    private int minBranches() {
        return getMinBranchesPerMethod().getOrElse(2);
    }

    private List<Path> resolveSourceRoots() {
        applyDefaultConventions();
        File sourceRoot = getSourceRoot().isPresent() ? getSourceRoot().get().getAsFile() : null;
        return new SourceRootResolver(
            getProject(),
            sourceRoot,
            getSourceRoots().getFiles(),
            getScanSubprojects().getOrElse(false)
        ).resolve();
    }

    private List<String> packagePrefixes() {
        if (extension == null) {
            return Collections.emptyList();
        }
        String includes = extension.getIncludes().getOrNull();
        if (includes == null || includes.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(includes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> dedupeRuleHeaders(List<String> rules) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> out = new ArrayList<>(rules.size());
        for (String rule : rules) {
            RuleHeader header = findRuleHeader(rule);
            String rewrittenRule = rule;
            if (header != null) {
                String originalName = header.name();
                int index = seen.merge(originalName, 1, Integer::sum);
                if (index > 1) {
                    String replacement = header.prefix() + originalName + "_" + index;
                    rewrittenRule = rule.substring(0, header.startIndex()) + replacement + rule.substring(header.endIndex());
                }
            }
            out.add(rewrittenRule);
        }
        return out;
    }

    private static RuleHeader findRuleHeader(String rule) {
        int lineStart = 0;
        while (lineStart < rule.length()) {
            int lineEnd = findLineEnd(rule, lineStart);
            RuleHeader header = parseRuleHeader(rule, lineStart, lineEnd);
            if (header != null) {
                return header;
            }
            lineStart = skipLineBreak(rule, lineEnd);
        }
        return null;
    }

    private static RuleHeader parseRuleHeader(String rule, int lineStart, int lineEnd) {
        int contentStart = skipInlineWhitespace(rule, lineStart, lineEnd);
        if (!matchesKeyword(rule, contentStart, lineEnd)) {
            return null;
        }

        int afterKeyword = contentStart + "RULE".length();
        if (afterKeyword >= lineEnd || !Character.isWhitespace(rule.charAt(afterKeyword))) {
            return null;
        }

        int nameStart = skipInlineWhitespace(rule, afterKeyword, lineEnd);
        if (nameStart >= lineEnd) {
            return null;
        }

        int nameEnd = trimInlineWhitespace(rule, nameStart, lineEnd);
        String prefix = rule.substring(lineStart, nameStart);
        String name = rule.substring(nameStart, nameEnd);
        return new RuleHeader(lineStart, lineEnd, prefix, name);
    }

    private static boolean matchesKeyword(String rule, int start, int lineEnd) {
        int keywordEnd = start + "RULE".length();
        return keywordEnd <= lineEnd && rule.regionMatches(start, "RULE", 0, "RULE".length());
    }

    private static int skipInlineWhitespace(String rule, int start, int end) {
        int cursor = start;
        while (cursor < end && Character.isWhitespace(rule.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int trimInlineWhitespace(String rule, int start, int end) {
        int cursor = end;
        while (cursor > start && Character.isWhitespace(rule.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }

    private static int findLineEnd(String rule, int lineStart) {
        int cursor = lineStart;
        while (cursor < rule.length()) {
            char current = rule.charAt(cursor);
            if (current == '\n' || current == '\r') {
                return cursor;
            }
            cursor++;
        }
        return rule.length();
    }

    private static int skipLineBreak(String rule, int lineEnd) {
        if (lineEnd >= rule.length()) {
            return rule.length();
        }
        if (rule.charAt(lineEnd) == '\r' && lineEnd + 1 < rule.length() && rule.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        return lineEnd + 1;
    }

    private void writeRules(Path outFile, List<String> rules) {
        try {
            createWriter(outFile).write(rules);
        } catch (UncheckedIOException e) {
            throw new GradleException("Failed writing BTM file " + outFile, e);
        }
    }

    private static BtmFileWriter createWriter(Path outFile) {
        try {
            return new BtmFileWriter(Clock.systemDefaultZone(), outFile);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return new BtmFileWriter(outFile);
        }
    }

    private FileTree sourceFilesFor(List<Path> roots) {
        ConfigurableFileCollection sourceFiles = getProject().files();
        roots.forEach(root -> sourceFiles.from(sourceInputFor(root)));
        return sourceFiles.getAsFileTree().matching(patterns -> patterns.include("**/*.java"));
    }

    private Object sourceInputFor(Path root) {
        if (Files.isDirectory(root)) {
            return getProject().fileTree(root.toFile(), spec -> spec.include("**/*.java"));
        }
        return root.toFile();
    }

    private record RuleHeader(int startIndex, int endIndex, String prefix, String name) { }

    private static final class SourceRootResolver {
        private final Project project;
        private final File sourceRootAlias;
        private final Set<File> explicitSourceRoots;
        private final boolean scanSubprojects;

        private SourceRootResolver(
            Project project,
            File sourceRootAlias,
            Set<File> explicitSourceRoots,
            boolean scanSubprojects
        ) {
            this.project = project;
            this.sourceRootAlias = sourceRootAlias;
            this.explicitSourceRoots = explicitSourceRoots;
            this.scanSubprojects = scanSubprojects;
        }

        private List<Path> resolve() {
            Set<Path> roots = new LinkedHashSet<>();
            addPath(roots, sourceRootAlias);
            explicitSourceRoots.forEach(root -> addPath(roots, root));
            addSourceSetRoots(project, roots);
            if (scanSubprojects) {
                project.getSubprojects().forEach(subproject -> addSourceSetRoots(subproject, roots));
            }
            return roots.stream()
                .filter(SourceRootResolver::isExistingSourceLocation)
                .toList();
        }

        private void addSourceSetRoots(Project candidate, Set<Path> roots) {
            SourceSetContainer sourceSets = candidate.getExtensions().findByType(SourceSetContainer.class);
            if (sourceSets == null) {
                return;
            }

            SourceSet mainSourceSet = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
            if (mainSourceSet == null) {
                return;
            }

            mainSourceSet.getAllJava().getSrcDirs().forEach(root -> addPath(roots, root));
        }

        private void addPath(Set<Path> roots, File root) {
            if (root == null) {
                return;
            }
            roots.add(root.toPath().toAbsolutePath().normalize());
        }

        private static boolean isExistingSourceLocation(Path path) {
            return Files.exists(path) && (Files.isDirectory(path) || Files.isRegularFile(path));
        }
    }
}
