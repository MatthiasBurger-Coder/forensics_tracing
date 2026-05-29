package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
        name = "btmgen",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class BtmGenMojo extends AbstractMavenForensicsSubmissionMojo {
    @Override
    protected String goalName() {
        return "btmgen";
    }
}
