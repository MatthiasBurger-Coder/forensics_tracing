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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans Java sources and renders Byteman rules using {@link GenerateRulesUseCase}.
 */
public abstract class GenerateBtmTask extends DefaultTask {
    private static final Pattern RULE_HEADER_PATTERN = Pattern.compile("(?m)^(\\s*RULE\\s+)(.+?)\\s*$");

    // ---- Configurable inputs ----
    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceRoot();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceRoots();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getOutputFile();

    @OutputDirectory
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

        // ✅ Use direct setters (no Provider lambdas) to avoid "Provider is not a functional interface"
        if (ext.getSourceRoot().isPresent()) {
            getSourceRoot().set(ext.getSourceRoot().get());
        }
        if (!ext.getSourceRoots().isEmpty()) {
            getSourceRoots().setFrom(ext.getSourceRoots());
        }
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
            throw new RuntimeException("Failed to create output directory for " + outFile, e);
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
        try {
            // Support both writer ctors to avoid signature drift issues
            BtmFileWriter writer;
            try {
                writer = new BtmFileWriter(Clock.systemDefaultZone(), outFile);
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                writer = new BtmFileWriter(outFile);
            }
            writer.write(dedupedRuleNames);
        } catch (Exception e) {
            throw new RuntimeException("Failed writing BTM file " + outFile, e);
        }

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
        if (getSourceRoots().isEmpty()) {
            getSourceRoots().from(getSourceRoot());
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
        Set<Path> roots = new LinkedHashSet<>();
        getSourceRoots().getFiles().stream()
            .map(File::toPath)
            .forEach(roots::add);
        if (getScanSubprojects().getOrElse(false)) {
            getProject().getRootProject().getAllprojects().forEach(project -> {
                Path candidate = project.getLayout().getProjectDirectory().dir("src/main/java").getAsFile().toPath();
                roots.add(candidate);
            });
        }
        if (roots.isEmpty() && getSourceRoot().isPresent()) {
            roots.add(getSourceRoot().get().getAsFile().toPath());
        }
        return roots.stream()
            .filter(Files::exists)
            .filter(Files::isDirectory)
            .toList();
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
            Matcher matcher = RULE_HEADER_PATTERN.matcher(rule);
            if (!matcher.find()) {
                out.add(rule);
                continue;
            }
            String prefix = matcher.group(1);
            String originalName = matcher.group(2).trim();
            int index = seen.merge(originalName, 1, Integer::sum);
            if (index == 1) {
                out.add(rule);
                continue;
            }
            String replacement = prefix + originalName + "_" + index;
            String rewritten = rule.substring(0, matcher.start()) + replacement + rule.substring(matcher.end());
            out.add(rewritten);
        }
        return out;
    }
}
