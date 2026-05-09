package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Maven aggregator goal that delegates reactor BTM generation to the shared runner.
 */
@Mojo(
        name = "btmgen-aggregate",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true,
        aggregator = true
)
public class BtmGenAggregateMojo extends AbstractAggregateBtmGenerationMojo {

    @Override
    public void execute() throws MojoExecutionException {
        try {
            BtmGenerationResult result = MavenForensicsMojoSupport.generateBtm(parameters(), getLog());
            getLog().info("Generated " + result.generatedRuleCount() + " BTM rules from Maven reactor.");
        } catch (IllegalArgumentException | BtmGenerationException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }
}
