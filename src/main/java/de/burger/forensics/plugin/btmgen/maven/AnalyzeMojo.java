package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
        name = "analyze",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class AnalyzeMojo extends AbstractMavenForensicsSubmissionMojo {
    @Override
    protected String goalName() {
        return "analyze";
    }
}
