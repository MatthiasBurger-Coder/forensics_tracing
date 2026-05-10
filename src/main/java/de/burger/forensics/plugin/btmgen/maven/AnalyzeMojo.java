package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationDefaults;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRunner;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisException;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisRunner;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven goal that runs BTM generation followed by optional Joern semantic enrichment.
 */
@Mojo(
        name = "analyze",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class AnalyzeMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forensics.sourceRoot")
    private File sourceRoot;

    @Parameter(property = "forensics.outputFile", defaultValue = "${project.build.directory}/forensics/generated.btm")
    private File outputFile;

    @Parameter(property = "forensics.cacheEnabled", defaultValue = "false")
    private boolean cacheEnabled;

    @Parameter(property = "forensics.cacheBackend", defaultValue = "h2")
    private String cacheBackend;

    @Parameter(property = "forensics.cacheDatabaseFile", defaultValue = "${project.build.directory}/forensics/cache/scan-cache")
    private File cacheDatabaseFile;

    @Parameter(property = "forensics.analysisStoreEnabled", defaultValue = "true")
    private boolean analysisStoreEnabled = true;

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

    @Parameter(property = "forensics.joernEnabled", defaultValue = "false")
    private boolean joernEnabled;

    @Parameter(property = "forensics.joernExecutable", defaultValue = "joern")
    private File joernExecutable;

    @Parameter(property = "forensics.joernParseExecutable", defaultValue = "joern-parse")
    private File joernParseExecutable;

    @Parameter(property = "forensics.joernSliceExecutable", defaultValue = "joern-slice")
    private File joernSliceExecutable;

    @Parameter(property = "forensics.joernWorkspaceDirectory", defaultValue = "${project.build.directory}/forensics/joern/workspace")
    private File joernWorkspaceDirectory;

    @Parameter(property = "forensics.joernOutputDirectory", defaultValue = "${project.build.directory}/forensics/joern")
    private File joernOutputDirectory;

    @Parameter(property = "forensics.joernMaxHeap", defaultValue = "")
    private String joernMaxHeap;

    @Parameter(property = "forensics.joernTimeoutSeconds", defaultValue = "300")
    private int joernTimeoutSeconds = 300;

    @Parameter(property = "forensics.joernFailOnError", defaultValue = "true")
    private boolean joernFailOnError = true;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            BtmGenerationRequest request = btmParameters().toGenerationRequest();
            BtmGenerationResult result = btmRunner().generate(request);
            semanticRunner().analyze(semanticParameters().toAnalysisRequest(request.sourceRoots()));
            getLog().info("Generated " + result.generatedRuleCount() + " BTM rules and imported Joern semantics.");
        } catch (IllegalArgumentException | BtmGenerationException | ForensicsSemanticAnalysisException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    protected BtmGenerationRunner btmRunner() {
        return new BtmGenerationRunner(StrategyRegistries.defaultRegistry(), new MavenLogAdapter(getLog()));
    }

    protected ForensicsSemanticAnalysisRunner semanticRunner() {
        return new ForensicsSemanticAnalysisRunner();
    }

    MavenBtmGenParameters btmParameters() {
        return new MavenBtmGenParameters(
                project,
                sourceRoot,
                outputFile,
                cacheEnabled,
                cacheBackend,
                cacheDatabaseFile,
                analysisStoreEnabled,
                analysisStoreDirectory,
                cleanupPolicy,
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
                includeTimestampHeader);
    }

    MavenSemanticAnalysisParameters semanticParameters() {
        return new MavenSemanticAnalysisParameters(
                project,
                sourceRoot,
                includeTests,
                joernEnabled,
                joernExecutable,
                joernParseExecutable,
                joernSliceExecutable,
                joernWorkspaceDirectory,
                joernOutputDirectory,
                joernMaxHeap,
                joernTimeoutSeconds,
                joernFailOnError,
                analysisStoreDirectory,
                manifestFile,
                checksumsFile,
                outputFile);
    }
}
