package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
        name = "analyze-aggregate",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        aggregator = true,
        threadSafe = true
)
public class AnalyzeAggregateMojo extends AbstractMavenForensicsSubmissionMojo {
    @Override
    protected String goalName() {
        return "analyze-aggregate";
    }
}
