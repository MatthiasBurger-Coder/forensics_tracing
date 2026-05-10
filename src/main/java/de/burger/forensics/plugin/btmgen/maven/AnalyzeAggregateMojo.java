package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisException;
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
 * Maven reactor goal that runs aggregate BTM generation followed by Joern semantic enrichment.
 */
@Mojo(
        name = "analyze-aggregate",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        aggregator = true,
        threadSafe = true
)
public class AnalyzeAggregateMojo extends AbstractMavenAnalysisMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            List<Path> sourceRoots = new MavenReactorSourceRootCollector().collect(reactorProjects(), includeTests());
            if (sourceRoots.isEmpty()) {
                throw new IllegalArgumentException("No existing Maven reactor source roots were found.");
            }
            BtmGenerationRequest request = btmParameters().toGenerationRequest(sourceRoots);
            BtmGenerationResult result = btmRunner().generate(request);
            semanticRunner().analyze(semanticParameters().toAnalysisRequest(sourceRoots));
            getLog().info("Generated " + result.generatedRuleCount() + " BTM rules from Maven reactor sources "
                    + "and imported Joern semantics.");
        } catch (IllegalArgumentException | BtmGenerationException | ForensicsSemanticAnalysisException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    MavenBtmGenParameters btmParameters() {
        return btmParameters(null);
    }

    MavenSemanticAnalysisParameters semanticParameters() {
        return semanticParameters(null);
    }

    private List<MavenProject> reactorProjects() {
        List<MavenProject> projects = session == null ? null : session.getProjects();
        if (projects == null || projects.isEmpty()) {
            return List.of(project());
        }
        return List.copyOf(projects);
    }
}
