package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

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
public class BtmGenMojo extends AbstractBtmGenerationMojo {

    @Override
    public void execute() throws MojoExecutionException {
        try {
            BtmGenerationResult result = MavenForensicsMojoSupport.generateBtm(parameters(), getLog());
            getLog().info("Generated " + result.generatedRuleCount() + " BTM rules.");
        } catch (IllegalArgumentException | BtmGenerationException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }
}
