package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;

/**
 * Maven goal that delegates BTM generation to the shared build-tool-neutral runner.
 */
@Mojo(
        name = "btmgen",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class BtmGenMojo extends AbstractMavenBtmGenerationMojo {

    @Parameter(property = "forensics.sourceRoot")
    private File sourceRoot;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            BtmGenerationRequest request = parameters().toGenerationRequest();
            BtmGenerationResult result = btmRunner().generate(request);
            getLog().info("Generated " + result.generatedRuleCount() + " BTM rules.");
        } catch (IllegalArgumentException | BtmGenerationException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    MavenBtmGenParameters parameters() {
        return btmParameters(sourceRoot);
    }
}
