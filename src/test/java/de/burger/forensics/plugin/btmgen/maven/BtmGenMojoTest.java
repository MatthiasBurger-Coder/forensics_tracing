package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.createSampleSource;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.createSource;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.projectWithBuildDirectory;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.setField;

class BtmGenMojoTest {

    @Test
    void executeFailsClearlyWhenNoSourceRootExists(@TempDir Path tempDir) throws Exception {
        BtmGenMojo mojo = mojo(projectWithBuildDirectory(tempDir));

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("No existing Maven source roots were found");
    }

    @Test
    void executeUsesExplicitSourceRoot(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = createSampleSource(tempDir.resolve("external/java"));
        Path outputFile = tempDir.resolve("target/forensics/generated.btm");
        BtmGenMojo mojo = mojo(projectWithBuildDirectory(tempDir));
        setField(mojo, "sourceRoot", sourceRoot.toFile());
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includePackages", "com.example");
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 2);

        mojo.execute();

        assertThat(Files.readString(outputFile)).contains("com.example.Sample");
    }

    @Test
    void executeUsesExplicitSourceRoots(@TempDir Path tempDir) throws Exception {
        Path firstSourceRoot = createSource(tempDir.resolve("external/first"), "com.example", "Sample");
        Path secondSourceRoot = createSource(tempDir.resolve("external/second"), "org.demo", "OtherSample");
        Path outputFile = tempDir.resolve("target/forensics/generated.btm");
        BtmGenMojo mojo = mojo(projectWithBuildDirectory(tempDir));
        setField(mojo, "sourceRoots", List.of(firstSourceRoot.toFile(), secondSourceRoot.toFile()));
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includePackages", "com.example,org.demo");
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 2);

        mojo.execute();

        assertThat(Files.readString(outputFile))
                .contains("com.example.Sample")
                .contains("org.demo.OtherSample");
    }

    @Test
    void executeWrapsGenerationFailures(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = createSampleSource(tempDir.resolve("src/main/java"));
        BtmGenMojo mojo = mojo(projectWithBuildDirectory(tempDir));
        setField(mojo, "sourceRoot", sourceRoot.toFile());
        setField(mojo, "outputFile", tempDir.resolve("target/forensics/generated.btm").toFile());
        setField(mojo, "cacheEnabled", true);
        setField(mojo, "cacheBackend", "sqlite");
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 2);

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("Unsupported parser scan cache backend: sqlite");
    }

    private static BtmGenMojo mojo(MavenProject project) throws Exception {
        BtmGenMojo mojo = new BtmGenMojo();
        mojo.setLog(new MavenMojoTestSupport.SilentLog());
        setField(mojo, "project", project);
        return mojo;
    }
}
