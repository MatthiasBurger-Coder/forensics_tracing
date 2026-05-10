package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

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
public class AnalyzeMojo extends AbstractMavenAnalysisMojo {

    @Parameter(property = "forensics.sourceRoot")
    private File sourceRoot;

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

    MavenBtmGenParameters btmParameters() {
        return btmParameters(sourceRoot);
    }

    MavenSemanticAnalysisParameters semanticParameters() {
        return semanticParameters(sourceRoot);
    }
}
