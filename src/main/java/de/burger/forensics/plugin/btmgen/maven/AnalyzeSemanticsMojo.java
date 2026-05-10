package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisException;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisRunner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven goal that imports optional Joern semantic enrichment into the analysis store.
 */
@Mojo(
        name = "analyze-semantics",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class AnalyzeSemanticsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forensics.sourceRoot")
    private File sourceRoot;

    @Parameter(property = "forensics.outputFile", defaultValue = "${project.build.directory}/forensics/generated.btm")
    private File outputFile;

    @Parameter(property = "forensics.analysisStoreDirectory", defaultValue = "${project.build.directory}/forensics/analysis-store")
    private File analysisStoreDirectory;

    @Parameter(property = "forensics.manifestFile", defaultValue = "${project.build.directory}/forensics/manifest.json")
    private File manifestFile;

    @Parameter(property = "forensics.checksumsFile", defaultValue = "${project.build.directory}/forensics/checksums.sha256")
    private File checksumsFile;

    @Parameter(property = "forensics.includeTests", defaultValue = "false")
    private boolean includeTests;

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
            semanticRunner().analyze(semanticParameters().toAnalysisRequest());
            getLog().info("Imported Joern semantic analysis artifacts into the forensics analysis store.");
        } catch (ForensicsSemanticAnalysisException | IllegalArgumentException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    protected ForensicsSemanticAnalysisRunner semanticRunner() {
        return new ForensicsSemanticAnalysisRunner();
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
