package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRunner;
import de.burger.forensics.plugin.btmgen.common.BtmTemplateRequest;
import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans Java sources and renders Byteman rules using {@link GenerateRulesUseCase}.
 */
@CacheableTask
public abstract class GenerateBtmTask extends DefaultTask {
    private StrategyRegistry registry = StrategyRegistries.defaultRegistry();

    @Inject
    @SuppressWarnings("java:S5993")
    public GenerateBtmTask() {
        getIncludeTimestampHeader().convention(false);
        getOutputs().doNotCacheIf(
                "Timestamp header is enabled and makes generated .btm output non-deterministic.",
                task -> getIncludeTimestampHeader().getOrElse(false)
        );
        getOutputs().doNotCacheIf(
                "Profiling writes local diagnostic state that should be refreshed on execution.",
                task -> getProfilingEnabled().getOrElse(false)
        );
    }

    // ---- Configurable inputs ----
    @Internal
    public abstract DirectoryProperty getSourceRoot();

    @Internal
    public abstract ConfigurableFileCollection getSourceRoots();

    @Internal
    public abstract ConfigurableFileCollection getCurrentProjectSourceSetRoots();

    @Internal
    public abstract ConfigurableFileCollection getSubprojectSourceSetRoots();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSourceFiles() {
        applyDefaultConventions();
        ConfigurableFileCollection sourceFiles = getObjectFactory().fileCollection();
        resolveSourceRoots().forEach(root -> sourceFiles.from(root.toFile()));
        return sourceFiles.getAsFileTree().matching(patterns -> patterns.include("**/*.java"));
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
    @Input @Optional public abstract Property<@NotNull String>  getIncludes();
    @Input @Optional public abstract Property<@NotNull Boolean> getScanSubprojects();
    @Input @Optional public abstract Property<@NotNull Boolean> getIncludeTimestampHeader();
    @Input @Optional public abstract Property<@NotNull String> getRegistryFingerprint();
    @Input @Optional public abstract Property<@NotNull Boolean> getCacheEnabled();
    @Input @Optional public abstract Property<@NotNull String> getCacheBackend();
    @Internal public abstract RegularFileProperty getCacheDatabaseFile();
    @LocalState @Optional public abstract DirectoryProperty getCacheDatabaseDirectory();
    @Input @Optional public abstract Property<@NotNull Boolean> getProfilingEnabled();
    @LocalState @Optional public abstract RegularFileProperty getProfileReportFile();
    @Input @Optional public abstract Property<@NotNull Boolean> getStrictParsing();
    @Input @Optional public abstract Property<@NotNull Boolean> getStrictConditionValidation();
    @Input @Optional public abstract Property<@NotNull Boolean> getDependencyAwareInvalidation();

    /** Injected via plugin apply() */
    public void setExtension(BtmGenExtension ext) {
        applyDefaultConventions();

        getSourceRoot().set(ext.getSourceRoot().get());
        getSourceRoots().setFrom(ext.getSourceRoots());
        File outputFile = ext.getOutputFile().get();
        getOutputFile().fileValue(outputFile);
        if (outputFile.getParentFile() != null) {
            getOutputDir().fileValue(outputFile.getParentFile());
        }
        getHelperFqn().convention(ext.getHelperFqn());
        getIncludes().convention(ext.getIncludes());
        getMinBranchesPerMethod().convention(ext.getMinBranchesPerMethod());
        getScanSubprojects().convention(ext.getScanSubprojects());
        getIncludeTimestampHeader().convention(ext.getIncludeTimestampHeader());
        getCacheEnabled().convention(ext.getCacheEnabled());
        getCacheBackend().convention(ext.getCacheBackend());
        getCacheDatabaseFile().set(getProjectLayout().file(ext.getCacheDatabaseFile()));
        getCacheDatabaseDirectory().convention(getProjectLayout().dir(ext.getCacheDatabaseFile().map(GenerateBtmTask::parentDirectory)));
        getProfilingEnabled().convention(ext.getProfilingEnabled());
        getProfileReportFile().set(getProjectLayout().file(ext.getProfileReportFile()));
        getStrictParsing().convention(ext.getStrictParsing());
        getStrictConditionValidation().convention(ext.getStrictConditionValidation());
        getDependencyAwareInvalidation().convention(ext.getDependencyAwareInvalidation());
        registry = ext.getRegistry() == null ? StrategyRegistries.defaultRegistry() : ext.getRegistry();
        getRegistryFingerprint().set(registryFingerprint(registry));
        configureSourceSetRoots();
    }

    @TaskAction
    public void generate() {
        applyDefaultConventions();
        try {
            new BtmGenerationRunner(activeRegistry(), new GradlePluginLogAdapter(getLogger()))
                    .generate(toGenerationRequest());
        } catch (BtmGenerationException exception) {
            throw new GradleException(exception.getMessage(), exception);
        }
    }

    private BtmGenerationRequest toGenerationRequest() {
        BtmGenerationRequest.Builder builder = BtmGenerationRequest.builder()
                .sourceRoots(resolveSourceRoots())
                .outputFile(getOutputFile().get().getAsFile().toPath())
                .cacheDatabaseFile(getCacheDatabaseFile().get().getAsFile().toPath())
                .profileReportFile(getProfileReportFile().get().getAsFile().toPath())
                .cacheEnabled(getCacheEnabled().getOrElse(false))
                .cacheBackend(getCacheBackend().getOrElse("h2"))
                .profilingEnabled(getProfilingEnabled().getOrElse(false))
                .strictParsing(getStrictParsing().getOrElse(false))
                .strictConditionValidation(getStrictConditionValidation().getOrElse(false))
                .dependencyAwareInvalidation(getDependencyAwareInvalidation().getOrElse(false))
                .includePackages(packagePrefixes())
                .helperFqn(resolveHelperFqn())
                .includeEntryExit(includeEntryExit())
                .minBranchesPerMethod(minBranches())
                .includeTimestampHeader(getIncludeTimestampHeader().getOrElse(false));

        if (hasMinimalInputs()) {
            builder.templateRequest(new BtmTemplateRequest(
                    templateIdOrDefault(),
                    getClassName().get(),
                    getMethodName().get(),
                    getMethodDesc().getOrNull()
            ));
        }
        return builder.build();
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    private void applyDefaultConventions() {
        ProjectLayout layout = getProjectLayout();
        conventionIfMissing(getSourceRoot(), layout.getProjectDirectory().dir("src/main/java"));
        conventionIfMissing(getOutputFile(), layout.getBuildDirectory().file("forensics/forensics.btm"));
        conventionIfMissing(getOutputDir(), layout.getBuildDirectory().dir("forensics"));
        conventionIfMissing(getIncludeEntryExit(), true);
        conventionIfMissing(getMinBranchesPerMethod(), 2);
        conventionIfMissing(getHelperFqn(), RuleParams.DEFAULT_HELPER_FQN);
        conventionIfMissing(getIncludes(), "");
        conventionIfMissing(getScanSubprojects(), false);
        conventionIfMissing(getIncludeTimestampHeader(), false);
        conventionIfMissing(getRegistryFingerprint(), registryFingerprint(registry));
        conventionIfMissing(getCacheEnabled(), false);
        conventionIfMissing(getCacheBackend(), "h2");
        conventionIfMissing(getCacheDatabaseFile(), layout.getBuildDirectory().file("forensics/cache/scan-cache"));
        conventionIfMissing(getCacheDatabaseDirectory(), layout.getBuildDirectory().dir("forensics/cache"));
        conventionIfMissing(getProfilingEnabled(), false);
        conventionIfMissing(getProfileReportFile(), layout.getBuildDirectory().file("forensics/scan-profile.json"));
        conventionIfMissing(getStrictParsing(), false);
        conventionIfMissing(getStrictConditionValidation(), false);
        conventionIfMissing(getDependencyAwareInvalidation(), false);
    }

    private static <T> void conventionIfMissing(Property<T> property, T value) {
        if (!property.isPresent()) {
            property.convention(value);
        }
    }

    private static void conventionIfMissing(DirectoryProperty property, Directory value) {
        if (!property.isPresent()) {
            property.convention(value);
        }
    }

    private static void conventionIfMissing(DirectoryProperty property, Provider<Directory> value) {
        if (!property.isPresent()) {
            property.convention(value);
        }
    }

    private static void conventionIfMissing(RegularFileProperty property, Provider<RegularFile> value) {
        if (!property.isPresent()) {
            property.convention(value);
        }
    }

    private void configureSourceSetRoots() {
        getCurrentProjectSourceSetRoots().setFrom(mainSourceSetRoots(getProject()));
        List<File> subprojectRoots = getProject().getSubprojects().stream()
                .sorted(Comparator.comparing(Project::getPath))
                .flatMap(subproject -> mainSourceSetRoots(subproject).stream())
                .toList();
        getSubprojectSourceSetRoots().setFrom(subprojectRoots);
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
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        addSourceRoot(roots, getSourceRoot().getOrNull() == null ? null : getSourceRoot().get().getAsFile());
        sortedFiles(getSourceRoots().getFiles()).forEach(file -> addSourceRoot(roots, file));
        sortedFiles(getCurrentProjectSourceSetRoots().getFiles()).forEach(file -> addSourceRoot(roots, file));
        if (getScanSubprojects().getOrElse(false)) {
            sortedFiles(getSubprojectSourceSetRoots().getFiles()).forEach(file -> addSourceRoot(roots, file));
        }
        return roots.stream()
                .filter(GenerateBtmTask::isExistingSourceLocation)
                .toList();
    }

    private List<String> packagePrefixes() {
        String includes = getIncludes().getOrElse("");
        if (includes.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(includes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static Set<File> mainSourceSetRoots(Project candidate) {
        SourceSetContainer sourceSets = candidate.getExtensions().findByType(SourceSetContainer.class);
        if (sourceSets == null) {
            return Set.of();
        }

        SourceSet mainSourceSet = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (mainSourceSet == null) {
            return Set.of();
        }

        return mainSourceSet.getAllJava().getSrcDirs();
    }

    private static List<File> sortedFiles(Set<File> files) {
        return files.stream()
                .sorted(Comparator.comparing(file -> file.toPath().toAbsolutePath().normalize().toString()))
                .toList();
    }

    private static void addSourceRoot(Set<Path> roots, File root) {
        if (root == null) {
            return;
        }
        roots.add(root.toPath().toAbsolutePath().normalize());
    }

    private static File parentDirectory(File file) {
        File parent = file.getParentFile();
        return parent == null ? new File(".") : parent;
    }

    private static boolean isExistingSourceLocation(Path path) {
        return Files.exists(path) && (Files.isDirectory(path) || Files.isRegularFile(path));
    }

    private static String registryFingerprint(StrategyRegistry candidateRegistry) {
        return candidateRegistry.ids().stream()
                .sorted()
                .map(id -> id + "=" + candidateRegistry.find(id)
                        .map(strategy -> strategy.getClass().getName())
                        .orElse("missing"))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private StrategyRegistry activeRegistry() {
        if (registry != null) {
            return registry;
        }

        StrategyRegistry defaultRegistry = StrategyRegistries.defaultRegistry();
        String defaultFingerprint = registryFingerprint(defaultRegistry);
        String configuredFingerprint = getRegistryFingerprint().getOrElse(defaultFingerprint);
        if (!configuredFingerprint.equals(defaultFingerprint)) {
            throw new GradleException(
                    "Custom StrategyRegistry instances are not supported when the configuration cache restores this task. " +
                    "Disable the configuration cache for this build or use the built-in strategy registry."
            );
        }
        registry = defaultRegistry;
        return registry;
    }
}
