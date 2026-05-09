package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.SemanticEnrichmentRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.projectWithBuildDirectory;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.setField;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MavenJoernConfigurationParityTest {

    @Test
    void analyzeSemanticsMojoMapsJoernConfigurationIntoSharedRequest(@TempDir Path tempDir) throws Exception {
        MavenProject project = projectWithBuildDirectory(tempDir);
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src/main/java"));
        project.addCompileSourceRoot(sourceRoot.toString());
        AnalyzeSemanticsMojo mojo = new AnalyzeSemanticsMojo();
        setField(mojo, "project", project);
        setField(mojo, "joernExecutable", tempDir.resolve("bin/joern").toFile());
        setField(mojo, "joernParseExecutable", tempDir.resolve("bin/joern-parse").toFile());
        setField(mojo, "joernSliceExecutable", tempDir.resolve("bin/joern-slice").toFile());
        setField(mojo, "joernWorkspaceDirectory", tempDir.resolve("target/forensics/joern/workspace").toFile());
        setField(mojo, "joernOutputDirectory", tempDir.resolve("target/forensics/joern").toFile());
        setField(mojo, "joernTimeoutSeconds", 90);
        setField(mojo, "joernFailOnError", false);

        SemanticEnrichmentRequest request = mojo.semanticRequest();

        assertThat(request.sourceRoots()).containsExactly(sourceRoot.toAbsolutePath().normalize());
        assertThat(request.joernExecutable()).isEqualTo(tempDir.resolve("bin/joern").toAbsolutePath());
        assertThat(request.joernParseExecutable()).isEqualTo(tempDir.resolve("bin/joern-parse").toAbsolutePath());
        assertThat(request.joernSliceExecutable()).isEqualTo(tempDir.resolve("bin/joern-slice").toAbsolutePath());
        assertThat(request.joernTimeoutSeconds()).isEqualTo(90);
        assertThat(request.joernFailOnError()).isFalse();
        assertThat(request.manifestFile()).isEqualTo(tempDir.resolve("target/forensics/manifest.json").toAbsolutePath());
        assertThat(request.checksumsFile()).isEqualTo(tempDir.resolve("target/forensics/checksums.sha256").toAbsolutePath());
    }

    @Test
    void importSemanticsMojoFailsClearlyWhenJoernIsDisabled() {
        ImportSemanticsMojo mojo = new ImportSemanticsMojo();

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("joernEnabled=true");
    }

}
