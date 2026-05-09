package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationDefaults;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared Maven parameter mapping for BTM-generating goals.
 */
abstract class AbstractBtmGenerationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forensics.sourceRoot")
    private File sourceRoot;

    @Parameter(property = "forensics.sourceRoots")
    private List<File> sourceRoots;

    @Parameter(property = "forensics.outputFile", defaultValue = "${project.build.directory}/forensics/generated.btm")
    private File outputFile;

    @Parameter(property = "forensics.cacheEnabled", defaultValue = "false")
    private boolean cacheEnabled;

    @Parameter(property = "forensics.cacheBackend", defaultValue = "h2")
    private String cacheBackend;

    @Parameter(property = "forensics.cacheDatabaseFile", defaultValue = "${project.build.directory}/forensics/cache/scan-cache")
    private File cacheDatabaseFile;

    @Parameter(property = "forensics.analysisStoreEnabled", defaultValue = "true")
    private boolean analysisStoreEnabled;

    @Parameter(property = "forensics.analysisStoreDirectory", defaultValue = "${project.build.directory}/forensics/analysis-store")
    private File analysisStoreDirectory;

    @Parameter(property = "forensics.cleanupPolicy", defaultValue = "KEEP_ON_SUCCESS")
    private String cleanupPolicy;

    @Parameter(property = "forensics.projectKey")
    private String projectKey;

    @Parameter(property = "forensics.pluginVersion")
    private String pluginVersion;

    @Parameter(property = "forensics.manifestFile", defaultValue = "${project.build.directory}/forensics/manifest.json")
    private File manifestFile;

    @Parameter(property = "forensics.checksumsFile", defaultValue = "${project.build.directory}/forensics/checksums.sha256")
    private File checksumsFile;

    @Parameter(property = "forensics.profilingEnabled", defaultValue = "false")
    private boolean profilingEnabled;

    @Parameter(property = "forensics.profileReportFile", defaultValue = "${project.build.directory}/forensics/scan-profile.json")
    private File profileReportFile;

    @Parameter(property = "forensics.strictParsing", defaultValue = "false")
    private boolean strictParsing;

    @Parameter(property = "forensics.strictConditionValidation", defaultValue = "false")
    private boolean strictConditionValidation;

    @Parameter(property = "forensics.dependencyAwareInvalidation", defaultValue = "false")
    private boolean dependencyAwareInvalidation;

    @Parameter(property = "forensics.includePackages")
    private String includePackages;

    @Parameter(property = "forensics.excludePackages")
    private String excludePackages;

    @Parameter(property = "forensics.includeTests", defaultValue = "false")
    private boolean includeTests;

    @Parameter(property = "forensics.helperFqn", defaultValue = BtmGenerationDefaults.DEFAULT_HELPER_FQN)
    private String helperFqn;

    @Parameter(property = "forensics.includeEntryExit", defaultValue = "true")
    private boolean includeEntryExit;

    @Parameter(property = "forensics.minBranchesPerMethod", defaultValue = "2")
    private int minBranchesPerMethod;

    @Parameter(property = "forensics.includeTimestampHeader", defaultValue = "false")
    private boolean includeTimestampHeader;

    MavenBtmGenParameters parameters() {
        return parameters(List.of(), cleanupPolicy);
    }

    MavenBtmGenParameters parameters(List<Path> selectedSourceRoots) {
        return parameters(selectedSourceRoots, cleanupPolicy);
    }

    MavenBtmGenParameters parameters(List<Path> selectedSourceRoots, String effectiveCleanupPolicy) {
        return new MavenBtmGenParameters(
                project,
                selectedSourceRoots,
                sourceRoot,
                sourceRoots,
                outputFile,
                cacheEnabled,
                cacheBackend,
                cacheDatabaseFile,
                analysisStoreEnabled,
                analysisStoreDirectory,
                effectiveCleanupPolicy,
                projectKey,
                pluginVersion,
                manifestFile,
                checksumsFile,
                profilingEnabled,
                profileReportFile,
                strictParsing,
                strictConditionValidation,
                dependencyAwareInvalidation,
                includePackages,
                excludePackages,
                includeTests,
                helperFqn,
                includeEntryExit,
                minBranchesPerMethod,
                includeTimestampHeader
        );
    }

    MavenProject project() {
        return project;
    }

    boolean hasExplicitSourceRoots() {
        return sourceRoot != null || (sourceRoots != null && !sourceRoots.isEmpty());
    }

    boolean includeTests() {
        return includeTests;
    }

    boolean analysisStoreEnabled() {
        return analysisStoreEnabled;
    }
}
