package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.List;

/**
 * Maven reactor aggregation goal backed by the shared BTM generation runner.
 */
@Mojo(
        name = "btmgen-aggregate",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        aggregator = true,
        threadSafe = true
)
public class BtmGenAggregateMojo extends AbstractMavenBtmGenerationMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            List<Path> sourceRoots = new MavenReactorSourceRootCollector().collect(reactorProjects(), includeTests());
            if (sourceRoots.isEmpty()) {
                throw new IllegalArgumentException("No existing Maven reactor source roots were found.");
            }
            BtmGenerationRequest request = parameters().toGenerationRequest(sourceRoots);
            BtmGenerationResult result = btmRunner().generate(request);
            getLog().info("Generated " + result.generatedRuleCount() + " BTM rules from Maven reactor sources.");
        } catch (IllegalArgumentException | BtmGenerationException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    MavenBtmGenParameters parameters() {
        return btmParameters(null);
    }

    private List<MavenProject> reactorProjects() {
        List<MavenProject> projects = session == null ? null : session.getProjects();
        if (projects == null || projects.isEmpty()) {
            return List.of(project());
        }
        return List.copyOf(projects);
    }
}
