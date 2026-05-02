package de.burger.forensics.plugin.btmgen;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRunner;
import de.burger.forensics.plugin.btmgen.gradle.BtmGenExtension;
import de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTask;
import de.burger.forensics.plugin.btmgen.maven.BtmGenMojo;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BtmGenerationAdapterValidationTest {

    @Test
    void runnerGradleTaskAndMavenMojoProduceSameDeterministicBtmOutput(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = createFixtureSourceRoot(tempDir.resolve("src/main/java"));
        Path runnerOutput = tempDir.resolve("runner/generated.btm");
        Path secondRunnerOutput = tempDir.resolve("runner/generated-again.btm");
        Path gradleOutput = tempDir.resolve("gradle/generated.btm");
        Path mavenOutput = tempDir.resolve("maven/generated.btm");

        runSharedRunner(sourceRoot, runnerOutput, tempDir.resolve("runner/cache/scan-cache"), tempDir.resolve("runner/profile.json"));
        runSharedRunner(sourceRoot, secondRunnerOutput, tempDir.resolve("runner/cache/scan-cache-2"), tempDir.resolve("runner/profile-2.json"));
        runGradleTask(tempDir.resolve("gradle-project"), sourceRoot, gradleOutput);
        runMavenMojo(tempDir.resolve("maven-project"), sourceRoot, mavenOutput);

        String expected = Files.readString(runnerOutput);
        assertThat(expected).contains("com.example.Sample");
        assertThat(Files.readString(secondRunnerOutput)).isEqualTo(expected);
        assertThat(Files.readString(gradleOutput)).isEqualTo(expected);
        assertThat(Files.readString(mavenOutput)).isEqualTo(expected);
    }

    private static void runSharedRunner(Path sourceRoot, Path outputFile, Path cacheFile, Path profileFile) {
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoots(List.of(sourceRoot))
                .outputFile(outputFile)
                .cacheDatabaseFile(cacheFile)
                .profileReportFile(profileFile)
                .includePackages(List.of("com.example"))
                .includeEntryExit(true)
                .minBranchesPerMethod(2)
                .build();

        new BtmGenerationRunner().generate(request);
    }

    private static void runGradleTask(Path projectDirectory, Path sourceRoot, Path outputFile) throws Exception {
        Files.createDirectories(projectDirectory);
        var project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build();
        var extension = project.getObjects().newInstance(BtmGenExtension.class);
        extension.getSourceRoot().set(sourceRoot.toFile());
        extension.getOutputFile().set(outputFile.toFile());
        extension.getIncludes().set("com.example");
        extension.getMinBranchesPerMethod().set(2);

        GenerateBtmTask task = project.getTasks().register("generateBtmValidation", GenerateBtmTask.class).get();
        task.setExtension(extension);
        task.generate();
    }

    private static void runMavenMojo(Path projectDirectory, Path sourceRoot, Path outputFile) throws Exception {
        Files.createDirectories(projectDirectory);
        BtmGenMojo mojo = new BtmGenMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", projectWithBuildDirectory(projectDirectory));
        setField(mojo, "sourceRoot", sourceRoot.toFile());
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "cacheDatabaseFile", projectDirectory.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", projectDirectory.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includePackages", "com.example");
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 2);

        mojo.execute();
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

    private static Path createFixtureSourceRoot(Path sourceRoot) throws Exception {
        Path packageDirectory = sourceRoot.resolve("com/example");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("Sample.java"), """
                package com.example;
                public class Sample {
                  public int run(int value) {
                    int next = value + 1;
                    if (next > 0) { }
                    switch (next) { case 1 -> {} default -> {} }
                    helper(next);
                    return next;
                  }
                  private void helper(int value) {
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
        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
        }

        @Override
        public void info(CharSequence content, Throwable error) {
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
        }

        @Override
        public void error(CharSequence content, Throwable error) {
        }

        @Override
        public void error(Throwable error) {
        }
    }
}
