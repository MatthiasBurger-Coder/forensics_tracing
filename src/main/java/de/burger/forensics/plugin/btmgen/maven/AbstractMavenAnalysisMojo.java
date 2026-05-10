package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisRunner;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

abstract class AbstractMavenAnalysisMojo extends AbstractMavenBtmGenerationMojo {

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

    protected ForensicsSemanticAnalysisRunner semanticRunner() {
        return new ForensicsSemanticAnalysisRunner();
    }

    final MavenSemanticAnalysisParameters semanticParameters(File sourceRoot) {
        return new MavenSemanticAnalysisParameters(
                project(),
                sourceRoot,
                includeTests(),
                joernEnabled,
                joernExecutable,
                joernParseExecutable,
                joernSliceExecutable,
                joernWorkspaceDirectory,
                joernOutputDirectory,
                joernMaxHeap,
                joernTimeoutSeconds,
                joernFailOnError,
                analysisStoreDirectory(),
                manifestFile(),
                checksumsFile(),
                outputFile());
    }
}
