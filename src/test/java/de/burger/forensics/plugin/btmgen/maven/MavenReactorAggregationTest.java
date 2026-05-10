package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MavenReactorAggregationTest {

    @Test
    void aggregateGoalScansReactorModulesWhenRootPomHasNoSources(@TempDir Path tempDir) throws Exception {
        MavenProject root = project("root", "pom", tempDir);
        MavenProject moduleA = project("module-a", "jar", tempDir.resolve("module-a"));
        MavenProject moduleB = project("module-b", "jar", tempDir.resolve("module-b"));
        MavenProject emptyModule = project("module-empty", "jar", tempDir.resolve("module-empty"));
        Path moduleARoot = writeSource(tempDir.resolve("module-a/src/main/java"), "ModuleAService");
        Path moduleBRoot = writeSource(tempDir.resolve("module-b/src/main/java"), "ModuleBService");
        moduleA.addCompileSourceRoot(moduleARoot.toString());
        moduleB.addCompileSourceRoot(moduleBRoot.toString());
        emptyModule.addCompileSourceRoot(tempDir.resolve("module-empty/src/main/java").toString());
        Path outputFile = tempDir.resolve("target/forensics/reactor.btm");

        BtmGenAggregateMojo mojo = aggregateMojo(root, List.of(root, moduleA, moduleB, emptyModule));
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "analysisStoreEnabled", false);
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 0);

        mojo.execute();

        String btm = Files.readString(outputFile);
        assertThat(btm)
                .contains("ModuleAService", "ModuleBService")
                .doesNotContain("module-empty");
    }

    @Test
    void aggregateGoalIncludesTestRootsWhenConfigured(@TempDir Path tempDir) throws Exception {
        MavenProject root = project("root", "pom", tempDir);
        MavenProject module = project("module", "jar", tempDir.resolve("module"));
        Path mainRoot = writeSource(tempDir.resolve("module/src/main/java"), "MainService");
        Path testRoot = writeSource(tempDir.resolve("module/src/test/java"), "TestService");
        module.addCompileSourceRoot(mainRoot.toString());
        module.addTestCompileSourceRoot(testRoot.toString());
        Path outputFile = tempDir.resolve("target/forensics/reactor-tests.btm");

        BtmGenAggregateMojo mojo = aggregateMojo(root, List.of(root, module));
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "analysisStoreEnabled", false);
        setField(mojo, "includeTests", true);
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 0);

        mojo.execute();

        assertThat(Files.readString(outputFile)).contains("MainService", "TestService");
    }

    @Test
    void aggregateGoalFallsBackToCurrentProjectWhenSessionIsMissing(@TempDir Path tempDir) throws Exception {
        MavenProject root = project("root", "jar", tempDir);
        Path sourceRoot = writeSource(tempDir.resolve("src/main/java"), "RootService");
        root.addCompileSourceRoot(sourceRoot.toString());
        Path outputFile = tempDir.resolve("target/forensics/current-project.btm");

        BtmGenAggregateMojo mojo = new BtmGenAggregateMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", root);
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "analysisStoreEnabled", false);
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 0);

        mojo.execute();

        assertThat(Files.readString(outputFile)).contains("RootService");
    }

    @Test
    void aggregateGoalFallsBackToCurrentProjectWhenSessionHasNoProjects(@TempDir Path tempDir) throws Exception {
        MavenProject root = project("root", "jar", tempDir);
        Path sourceRoot = writeSource(tempDir.resolve("src/main/java"), "EmptySessionService");
        root.addCompileSourceRoot(sourceRoot.toString());
        Path outputFile = tempDir.resolve("target/forensics/empty-session.btm");

        BtmGenAggregateMojo mojo = aggregateMojo(root, List.of());
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "analysisStoreEnabled", false);
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 0);

        mojo.execute();

        assertThat(Files.readString(outputFile)).contains("EmptySessionService");
    }

    @Test
    void aggregateGoalFailsClearlyWhenReactorHasNoSourceRoots(@TempDir Path tempDir) throws Exception {
        MavenProject root = project("root", "pom", tempDir);
        BtmGenAggregateMojo mojo = aggregateMojo(root, List.of(root));

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("No existing Maven reactor source roots were found");
    }

    private static BtmGenAggregateMojo aggregateMojo(MavenProject root, List<MavenProject> projects) throws Exception {
        MavenSession session = mock(MavenSession.class);
        when(session.getProjects()).thenReturn(projects);
        BtmGenAggregateMojo mojo = new BtmGenAggregateMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", root);
        setField(mojo, "session", session);
        return mojo;
    }

    private static MavenProject project(String artifactId, String packaging, Path basedir) {
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        model.setPackaging(packaging);
        MavenProject project = new MavenProject(model);
        project.setFile(basedir.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(basedir.resolve("target").toString());
        project.setBuild(build);
        return project;
    }

    private static Path writeSource(Path sourceRoot, String className) throws Exception {
        Path packageDirectory = sourceRoot.resolve("com/example");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve(className + ".java"), """
                package com.example;
                public class %s {
                  public int run(int value) {
                    if (value > 0) { }
                    return value;
                  }
                }
                """.formatted(className));
        return sourceRoot;
    }

    private static void setField(BtmGenAggregateMojo mojo, String name, Object value) throws Exception {
        Class<?> current = mojo.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                field.set(mojo, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class SilentLog implements Log {
        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void debug(Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void info(Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void warn(Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void error(Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }
    }
}
