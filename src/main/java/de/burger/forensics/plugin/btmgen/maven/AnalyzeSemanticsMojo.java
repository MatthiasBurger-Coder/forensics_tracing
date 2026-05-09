package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationDefaults;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.SemanticEnrichmentRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;

/**
 * Maven goal that runs optional Joern semantic enrichment for an existing analysis package.
 */
@Mojo(
        name = "analyze-semantics",
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class AnalyzeSemanticsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forensics.sourceRoot")
    private File sourceRoot;

    @Parameter(property = "forensics.sourceRoots")
    private List<File> sourceRoots;

    @Parameter(property = "forensics.outputFile", defaultValue = "${project.build.directory}/forensics/generated.btm")
    private File outputFile;

    @Parameter(property = "forensics.cacheDatabaseFile", defaultValue = "${project.build.directory}/forensics/cache/scan-cache")
    private File cacheDatabaseFile;

    @Parameter(property = "forensics.analysisStoreDirectory", defaultValue = "${project.build.directory}/forensics/analysis-store")
    private File analysisStoreDirectory;

    @Parameter(property = "forensics.manifestFile", defaultValue = "${project.build.directory}/forensics/manifest.json")
    private File manifestFile;

    @Parameter(property = "forensics.checksumsFile", defaultValue = "${project.build.directory}/forensics/checksums.sha256")
    private File checksumsFile;

    @Parameter(property = "forensics.profileReportFile", defaultValue = "${project.build.directory}/forensics/scan-profile.json")
    private File profileReportFile;

    @Parameter(property = "forensics.includePackages")
    private String includePackages;

    @Parameter(property = "forensics.excludePackages")
    private String excludePackages;

    @Parameter(property = "forensics.includeTests", defaultValue = "false")
    private boolean includeTests;

    @Parameter(property = "forensics.helperFqn", defaultValue = BtmGenerationDefaults.DEFAULT_HELPER_FQN)
    private String helperFqn;

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

    @Parameter(property = "forensics.joernTimeoutSeconds", defaultValue = "300")
    private int joernTimeoutSeconds;

    @Parameter(property = "forensics.joernFailOnError", defaultValue = "true")
    private boolean joernFailOnError;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            MavenForensicsMojoSupport.requireJoernEnabled(joernEnabled, "forensics:analyze-semantics");
            MavenForensicsMojoSupport.analyzeSemantics(semanticRequest());
            getLog().info("Imported Joern semantic artifacts into the forensics Analysis Store.");
        } catch (IllegalArgumentException | BtmGenerationException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    SemanticEnrichmentRequest semanticRequest() {
        BtmGenerationRequest generationRequest = new MavenBtmGenParameters(
                project,
                List.of(),
                sourceRoot,
                sourceRoots,
                outputFile,
                false,
                BtmGenerationDefaults.DEFAULT_CACHE_BACKEND,
                cacheDatabaseFile,
                true,
                analysisStoreDirectory,
                BtmGenerationDefaults.defaultCleanupPolicy(),
                null,
                null,
                manifestFile,
                checksumsFile,
                false,
                profileReportFile,
                false,
                false,
                false,
                includePackages,
                excludePackages,
                includeTests,
                helperFqn,
                true,
                BtmGenerationDefaults.DEFAULT_MIN_BRANCHES_PER_METHOD,
                false
        ).toGenerationRequest();
        return MavenForensicsMojoSupport.semanticRequest(
                generationRequest,
                joernExecutable,
                joernParseExecutable,
                joernSliceExecutable,
                joernWorkspaceDirectory,
                joernOutputDirectory,
                joernTimeoutSeconds,
                joernFailOnError);
    }
}
