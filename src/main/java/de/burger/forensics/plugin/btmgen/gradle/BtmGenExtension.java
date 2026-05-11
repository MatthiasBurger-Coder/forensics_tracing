package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;

/**
 * Extension to configure source roots, output location, and rendering strategies.
 * <p>
 * The built-in {@link StrategyRegistries#defaultRegistry()} is configuration-cache safe.
 * Custom {@link StrategyRegistry} instances remain supported for normal builds, but they are
 * not guaranteed to restore safely when Gradle reuses the configuration cache.
 */
public class BtmGenExtension {
    private StrategyRegistry registry = StrategyRegistries.defaultRegistry();
    private final Property<@NotNull File> sourceRoot;
    private final ConfigurableFileCollection sourceRoots;
    private final Property<@NotNull File> outputFile;
    private final Property<@NotNull String> includes;
    private final Property<@NotNull String> excludes;
    private final Property<@NotNull Boolean> includeTests;
    private final Property<@NotNull String> helperFqn;
    private final Property<@NotNull Integer> minBranchesPerMethod;
    private final Property<@NotNull Boolean> scanSubprojects;
    private final Property<@NotNull Boolean> includeTimestampHeader;
    private final Property<@NotNull Boolean> cacheEnabled;
    private final Property<@NotNull File> cacheDatabaseFile;
    private final Property<@NotNull String> cacheBackend;
    private final Property<@NotNull Boolean> analysisStoreEnabled;
    private final Property<@NotNull File> analysisStoreDirectory;
    private final Property<@NotNull String> cleanupPolicy;
    private final Property<@NotNull String> projectKey;
    private final Property<@NotNull File> manifestFile;
    private final Property<@NotNull File> checksumsFile;
    private final Property<@NotNull Boolean> engineRequestEnabled;
    private final Property<@NotNull File> engineRequestFile;
    private final Property<@NotNull Boolean> profilingEnabled;
    private final Property<@NotNull File> profileReportFile;
    private final Property<@NotNull Boolean> strictParsing;
    private final Property<@NotNull Boolean> strictConditionValidation;
    private final Property<@NotNull Boolean> dependencyAwareInvalidation;
    private final Property<@NotNull Boolean> joernEnabled;
    private final Property<@NotNull File> joernExecutable;
    private final Property<@NotNull File> joernParseExecutable;
    private final Property<@NotNull File> joernSliceExecutable;
    private final Property<@NotNull File> joernWorkspaceDirectory;
    private final Property<@NotNull File> joernOutputDirectory;
    private final Property<@NotNull String> joernMaxHeap;
    private final Property<@NotNull Integer> joernTimeoutSeconds;
    private final Property<@NotNull Boolean> joernFailOnError;

    @Inject
    public BtmGenExtension(ObjectFactory objects) {
        this.sourceRoot = objects.property(File.class);
        this.sourceRoots = objects.fileCollection();
        this.outputFile = objects.property(File.class);
        this.includes = objects.property(String.class);
        this.excludes = objects.property(String.class);
        this.includeTests = objects.property(Boolean.class);
        this.helperFqn = objects.property(String.class);
        this.minBranchesPerMethod = objects.property(Integer.class);
        this.scanSubprojects = objects.property(Boolean.class);
        this.includeTimestampHeader = objects.property(Boolean.class);
        this.cacheEnabled = objects.property(Boolean.class);
        this.cacheDatabaseFile = objects.property(File.class);
        this.cacheBackend = objects.property(String.class);
        this.analysisStoreEnabled = objects.property(Boolean.class);
        this.analysisStoreDirectory = objects.property(File.class);
        this.cleanupPolicy = objects.property(String.class);
        this.projectKey = objects.property(String.class);
        this.manifestFile = objects.property(File.class);
        this.checksumsFile = objects.property(File.class);
        this.engineRequestEnabled = objects.property(Boolean.class);
        this.engineRequestFile = objects.property(File.class);
        this.profilingEnabled = objects.property(Boolean.class);
        this.profileReportFile = objects.property(File.class);
        this.strictParsing = objects.property(Boolean.class);
        this.strictConditionValidation = objects.property(Boolean.class);
        this.dependencyAwareInvalidation = objects.property(Boolean.class);
        this.joernEnabled = objects.property(Boolean.class);
        this.joernExecutable = objects.property(File.class);
        this.joernParseExecutable = objects.property(File.class);
        this.joernSliceExecutable = objects.property(File.class);
        this.joernWorkspaceDirectory = objects.property(File.class);
        this.joernOutputDirectory = objects.property(File.class);
        this.joernMaxHeap = objects.property(String.class);
        this.joernTimeoutSeconds = objects.property(Integer.class);
        this.joernFailOnError = objects.property(Boolean.class);
        this.sourceRoot.convention(new File("src/main/java"));
        this.outputFile.convention(new File("build/forensics/forensics.btm"));
        this.excludes.convention("");
        this.includeTests.convention(false);
        this.helperFqn.convention(RuleParams.DEFAULT_HELPER_FQN);
        this.minBranchesPerMethod.convention(2);
        this.scanSubprojects.convention(false);
        this.includeTimestampHeader.convention(false);
        this.cacheEnabled.convention(false);
        this.cacheDatabaseFile.convention(new File("build/forensics/cache/scan-cache"));
        this.cacheBackend.convention("h2");
        this.analysisStoreEnabled.convention(true);
        this.analysisStoreDirectory.convention(new File("build/forensics/analysis-store"));
        this.cleanupPolicy.convention("KEEP_ON_SUCCESS");
        this.manifestFile.convention(new File("build/forensics/manifest.json"));
        this.checksumsFile.convention(new File("build/forensics/checksums.sha256"));
        this.engineRequestEnabled.convention(false);
        this.engineRequestFile.convention(new File("build/forensics/engine-request.json"));
        this.profilingEnabled.convention(false);
        this.profileReportFile.convention(new File("build/forensics/scan-profile.json"));
        this.strictParsing.convention(false);
        this.strictConditionValidation.convention(false);
        this.dependencyAwareInvalidation.convention(false);
        this.joernEnabled.convention(false);
        this.joernExecutable.convention(new File("joern"));
        this.joernParseExecutable.convention(new File("joern-parse"));
        this.joernSliceExecutable.convention(new File("joern-slice"));
        this.joernWorkspaceDirectory.convention(new File("build/forensics/joern/workspace"));
        this.joernOutputDirectory.convention(new File("build/forensics/joern"));
        this.joernMaxHeap.convention("");
        this.joernTimeoutSeconds.convention(300);
        this.joernFailOnError.convention(true);
    }

    public StrategyRegistry getRegistry() {
        return registry;
    }

    /**
     * Sets a custom registry for rule rendering.
     * <p>
     * For configuration-cache-enabled builds, prefer the built-in registry unless the build
     * is prepared to avoid cache reuse for this task.
     */
    public void setRegistry(StrategyRegistry registry) {
        this.registry = registry == null ? StrategyRegistries.defaultRegistry() : registry;
    }

    public Property<@NotNull File> getSourceRoot() {
        return sourceRoot;
    }

    public ConfigurableFileCollection getSourceRoots() {
        return sourceRoots;
    }

    public Property<@NotNull File> getOutputFile() {
        return outputFile;
    }

    public Property<@NotNull String> getIncludes() {
        return includes;
    }

    public Property<@NotNull String> getExcludes() {
        return excludes;
    }

    public Property<@NotNull Boolean> getIncludeTests() {
        return includeTests;
    }

    public Property<@NotNull String> getHelperFqn() {
        return helperFqn;
    }

    public Property<@NotNull Integer> getMinBranchesPerMethod() {
        return minBranchesPerMethod;
    }

    public Property<@NotNull Boolean> getScanSubprojects() {
        return scanSubprojects;
    }

    public Property<@NotNull Boolean> getIncludeTimestampHeader() {
        return includeTimestampHeader;
    }

    public Property<@NotNull Boolean> getCacheEnabled() {
        return cacheEnabled;
    }

    public Property<@NotNull File> getCacheDatabaseFile() {
        return cacheDatabaseFile;
    }

    public Property<@NotNull String> getCacheBackend() {
        return cacheBackend;
    }

    public Property<@NotNull Boolean> getAnalysisStoreEnabled() {
        return analysisStoreEnabled;
    }

    public Property<@NotNull File> getAnalysisStoreDirectory() {
        return analysisStoreDirectory;
    }

    public Property<@NotNull String> getCleanupPolicy() {
        return cleanupPolicy;
    }

    public Property<@NotNull String> getProjectKey() {
        return projectKey;
    }

    public Property<@NotNull File> getManifestFile() {
        return manifestFile;
    }

    public Property<@NotNull File> getChecksumsFile() {
        return checksumsFile;
    }

    public Property<@NotNull Boolean> getEngineRequestEnabled() {
        return engineRequestEnabled;
    }

    public Property<@NotNull File> getEngineRequestFile() {
        return engineRequestFile;
    }

    public Property<@NotNull Boolean> getProfilingEnabled() {
        return profilingEnabled;
    }

    public Property<@NotNull File> getProfileReportFile() {
        return profileReportFile;
    }

    public Property<@NotNull Boolean> getStrictParsing() {
        return strictParsing;
    }

    public Property<@NotNull Boolean> getStrictConditionValidation() {
        return strictConditionValidation;
    }

    public Property<@NotNull Boolean> getDependencyAwareInvalidation() {
        return dependencyAwareInvalidation;
    }

    public Property<@NotNull Boolean> getJoernEnabled() {
        return joernEnabled;
    }

    public Property<@NotNull File> getJoernExecutable() {
        return joernExecutable;
    }

    public Property<@NotNull File> getJoernParseExecutable() {
        return joernParseExecutable;
    }

    public Property<@NotNull File> getJoernSliceExecutable() {
        return joernSliceExecutable;
    }

    public Property<@NotNull File> getJoernWorkspaceDirectory() {
        return joernWorkspaceDirectory;
    }

    public Property<@NotNull File> getJoernOutputDirectory() {
        return joernOutputDirectory;
    }

    public Property<@NotNull String> getJoernMaxHeap() {
        return joernMaxHeap;
    }

    public Property<@NotNull Integer> getJoernTimeoutSeconds() {
        return joernTimeoutSeconds;
    }

    public Property<@NotNull Boolean> getJoernFailOnError() {
        return joernFailOnError;
    }
}
