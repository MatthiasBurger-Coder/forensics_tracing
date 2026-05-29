package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
        name = "submit-analysis",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class SubmitAnalysisMojo extends AbstractMavenForensicsSubmissionMojo {
    @Override
    protected String goalName() {
        return "submit-analysis";
    }
}
