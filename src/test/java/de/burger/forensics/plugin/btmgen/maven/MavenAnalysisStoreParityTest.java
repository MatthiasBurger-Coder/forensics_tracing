package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MavenAnalysisStoreParityTest {

    @Test
    void btmGenMojoWritesAnalysisStoreManifestAndChecksums(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = createSampleSource(tempDir.resolve("src/main/java"));
        Path outputFile = tempDir.resolve("target/forensics/generated.btm");
        Path analysisStoreDirectory = tempDir.resolve("target/forensics/analysis-store");
        Path manifestFile = tempDir.resolve("target/forensics/manifest.json");
        Path checksumsFile = tempDir.resolve("target/forensics/checksums.sha256");
        BtmGenMojo mojo = new BtmGenMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", projectWithBuildDirectory(tempDir));
        setField(mojo, "sourceRoot", sourceRoot.toFile());
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "analysisStoreEnabled", true);
        setField(mojo, "analysisStoreDirectory", analysisStoreDirectory.toFile());
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "checksumsFile", checksumsFile.toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 2);

        mojo.execute();

        assertThat(outputFile).exists();
        assertThat(analysisStoreDirectory.resolve("analysis-store.mv.db")).exists();
        assertThat(Files.readString(manifestFile)).contains("\"projectKey\": \"de.burger.forensics:sample\"");
        assertThat(Files.readString(checksumsFile)).contains("generated.btm", "manifest.json", "analysis-store/");
    }

    private static MavenProject projectWithBuildDirectory(Path projectDirectory) {
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId("sample");
        model.setVersion("1.0.0");
        MavenProject project = new MavenProject(model);
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(projectDirectory.resolve("target").toString());
        project.setBuild(build);
        return project;
    }

    private static Path createSampleSource(Path sourceRoot) throws Exception {
        Path packageDirectory = sourceRoot.resolve("com/example");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("Sample.java"), """
                package com.example;
                public class Sample {
                  public int run(int value) {
                    if (value > 0) { }
                    return value;
                  }
                }
                """);
        return sourceRoot;
    }

    private static void setField(BtmGenMojo mojo, String name, Object value) throws Exception {
        Field field = BtmGenMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }

    private static final class SilentLog implements Log {
        @Override public boolean isDebugEnabled() { return true; }
        @Override public void debug(CharSequence content) { }
        @Override public void debug(CharSequence content, Throwable error) { }
        @Override public void debug(Throwable error) { }
        @Override public boolean isInfoEnabled() { return true; }
        @Override public void info(CharSequence content) { }
        @Override public void info(CharSequence content, Throwable error) { }
        @Override public void info(Throwable error) { }
        @Override public boolean isWarnEnabled() { return true; }
        @Override public void warn(CharSequence content) { }
        @Override public void warn(CharSequence content, Throwable error) { }
        @Override public void warn(Throwable error) { }
        @Override public boolean isErrorEnabled() { return true; }
        @Override public void error(CharSequence content) { }
        @Override public void error(CharSequence content, Throwable error) { }
        @Override public void error(Throwable error) { }
    }
}
