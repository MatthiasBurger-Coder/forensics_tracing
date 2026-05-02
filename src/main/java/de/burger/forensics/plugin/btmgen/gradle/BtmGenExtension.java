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
    private final Property<@NotNull String> helperFqn;
    private final Property<@NotNull Integer> minBranchesPerMethod;
    private final Property<@NotNull Boolean> scanSubprojects;
    private final Property<@NotNull Boolean> includeTimestampHeader;
    private final Property<@NotNull Boolean> cacheEnabled;
    private final Property<@NotNull File> cacheDatabaseFile;
    private final Property<@NotNull String> cacheBackend;
    private final Property<@NotNull Boolean> profilingEnabled;
    private final Property<@NotNull File> profileReportFile;
    private final Property<@NotNull Boolean> strictParsing;
    private final Property<@NotNull Boolean> dependencyAwareInvalidation;

    @Inject
    public BtmGenExtension(ObjectFactory objects) {
        this.sourceRoot = objects.property(File.class);
        this.sourceRoots = objects.fileCollection();
        this.outputFile = objects.property(File.class);
        this.includes = objects.property(String.class);
        this.helperFqn = objects.property(String.class);
        this.minBranchesPerMethod = objects.property(Integer.class);
        this.scanSubprojects = objects.property(Boolean.class);
        this.includeTimestampHeader = objects.property(Boolean.class);
        this.cacheEnabled = objects.property(Boolean.class);
        this.cacheDatabaseFile = objects.property(File.class);
        this.cacheBackend = objects.property(String.class);
        this.profilingEnabled = objects.property(Boolean.class);
        this.profileReportFile = objects.property(File.class);
        this.strictParsing = objects.property(Boolean.class);
        this.dependencyAwareInvalidation = objects.property(Boolean.class);
        this.sourceRoot.convention(new File("src/main/java"));
        this.outputFile.convention(new File("build/forensics/forensics.btm"));
        this.helperFqn.convention(RuleParams.DEFAULT_HELPER_FQN);
        this.minBranchesPerMethod.convention(2);
        this.scanSubprojects.convention(false);
        this.includeTimestampHeader.convention(false);
        this.cacheEnabled.convention(false);
        this.cacheDatabaseFile.convention(new File("build/forensics/cache/scan-cache"));
        this.cacheBackend.convention("h2");
        this.profilingEnabled.convention(false);
        this.profileReportFile.convention(new File("build/forensics/scan-profile.json"));
        this.strictParsing.convention(false);
        this.dependencyAwareInvalidation.convention(false);
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

    public Property<@NotNull Boolean> getProfilingEnabled() {
        return profilingEnabled;
    }

    public Property<@NotNull File> getProfileReportFile() {
        return profileReportFile;
    }

    public Property<@NotNull Boolean> getStrictParsing() {
        return strictParsing;
    }

    public Property<@NotNull Boolean> getDependencyAwareInvalidation() {
        return dependencyAwareInvalidation;
    }
}
