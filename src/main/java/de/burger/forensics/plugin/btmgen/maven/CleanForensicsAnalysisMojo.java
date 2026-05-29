package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
        name = "clean-analysis",
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class CleanForensicsAnalysisMojo extends AbstractMojo {
    @Override
    public void execute() {
        getLog().info("No local forensics analysis artifacts are owned by this Maven plugin.");
    }
}
