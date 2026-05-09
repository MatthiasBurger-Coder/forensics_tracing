package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationDefaults;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;

/**
 * Maven reactor full analysis goal for BTM generation plus optional Joern enrichment.
 */
@Mojo(
        name = "analyze-aggregate",
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true,
        aggregator = true
)
public class AnalyzeAggregateMojo extends AbstractAggregateBtmGenerationMojo {

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
            MavenForensicsMojoSupport.requireJoernEnabled(joernEnabled, "forensics:analyze-aggregate");
            MavenForensicsMojoSupport.requireAnalysisStoreEnabled(analysisStoreEnabled(), "forensics:analyze-aggregate");
            MavenBtmGenParameters generationParameters = generationParameters();
            BtmGenerationRequest request = generationParameters.toGenerationRequest();
            BtmGenerationResult result = MavenForensicsMojoSupport.generateBtm(generationParameters, getLog());
            MavenForensicsMojoSupport.analyzeSemantics(MavenForensicsMojoSupport.semanticRequest(
                    request,
                    joernExecutable,
                    joernParseExecutable,
                    joernSliceExecutable,
                    joernWorkspaceDirectory,
                    joernOutputDirectory,
                    joernTimeoutSeconds,
                    joernFailOnError));
            getLog().info("Generated " + result.generatedRuleCount() + " reactor BTM rules and imported Joern semantic artifacts.");
        } catch (IllegalArgumentException | BtmGenerationException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    MavenBtmGenParameters generationParameters() {
        return parameters(BtmGenerationDefaults.defaultCleanupPolicy());
    }
}
