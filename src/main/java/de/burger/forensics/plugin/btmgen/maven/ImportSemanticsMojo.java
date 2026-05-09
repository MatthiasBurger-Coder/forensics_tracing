package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

/**
 * Maven goal that verifies imported Joern semantic artifacts.
 */
@Mojo(
        name = "import-semantics",
        requiresProject = true,
        threadSafe = true
)
public class ImportSemanticsMojo extends AbstractMojo {

    @Parameter(property = "forensics.joernEnabled", defaultValue = "false")
    private boolean joernEnabled;

    @Parameter(property = "forensics.joernOutputDirectory", defaultValue = "${project.build.directory}/forensics/joern")
    private File joernOutputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        MavenForensicsMojoSupport.verifyImportedArtifacts(joernEnabled, joernOutputDirectory);
        getLog().info("Verified Joern semantic artifacts.");
    }
}
