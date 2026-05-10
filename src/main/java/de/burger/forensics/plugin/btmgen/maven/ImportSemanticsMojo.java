package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisException;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticImportVerifier;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven goal that verifies Joern semantic artifacts exist for a prior semantic analysis run.
 */
@Mojo(
        name = "import-semantics",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class ImportSemanticsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forensics.joernEnabled", defaultValue = "false")
    private boolean joernEnabled;

    @Parameter(property = "forensics.joernOutputDirectory", defaultValue = "${project.build.directory}/forensics/joern")
    private File joernOutputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            semanticImportVerifier().verify(
                    joernEnabled,
                    parameters().joernOutputDirectoryPath(),
                    "forensics.joernEnabled=true",
                    "analyze-semantics");
            getLog().info("Verified Joern semantic artifacts.");
        } catch (ForensicsSemanticAnalysisException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    protected ForensicsSemanticImportVerifier semanticImportVerifier() {
        return new ForensicsSemanticImportVerifier();
    }

    MavenSemanticAnalysisParameters parameters() {
        return new MavenSemanticAnalysisParameters(
                project,
                null,
                false,
                joernEnabled,
                null,
                null,
                null,
                null,
                joernOutputDirectory,
                "",
                300,
                true,
                null,
                null,
                null,
                null);
    }
}
