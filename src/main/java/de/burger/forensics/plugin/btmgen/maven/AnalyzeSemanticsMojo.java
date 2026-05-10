package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

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
public class AnalyzeSemanticsMojo extends AbstractMavenAnalysisMojo {

    @Parameter(property = "forensics.sourceRoot")
    private File sourceRoot;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            semanticRunner().analyze(semanticParameters().toAnalysisRequest());
            getLog().info("Imported Joern semantic analysis artifacts into the forensics analysis store.");
        } catch (ForensicsSemanticAnalysisException | IllegalArgumentException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    MavenSemanticAnalysisParameters semanticParameters() {
        return semanticParameters(sourceRoot);
    }
}
