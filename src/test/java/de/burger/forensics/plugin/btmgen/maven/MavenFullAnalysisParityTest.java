package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.adapters.persistence.h2.H2AnalysisStoreAdapter;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisRunner;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MavenFullAnalysisParityTest {

    @Test
    void analyzeGoalRunsBtmGenerationAndSemanticImport(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = writeSource(tempDir.resolve("src/main/java"), "ModuleService");
        Path outputFile = tempDir.resolve("target/forensics/generated.btm");
        Path manifestFile = tempDir.resolve("target/forensics/manifest.json");
        Path checksumsFile = tempDir.resolve("target/forensics/checksums.sha256");
        TestAnalyzeMojo mojo = new TestAnalyzeMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", project("sample", "jar", tempDir));
        setField(mojo, "sourceRoot", sourceRoot.toFile());
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "analysisStoreEnabled", true);
        setField(mojo, "analysisStoreDirectory", tempDir.resolve("target/forensics/analysis-store").toFile());
        setField(mojo, "cleanupPolicy", "KEEP_ON_SUCCESS");
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "checksumsFile", checksumsFile.toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 0);
        setField(mojo, "joernEnabled", true);

        mojo.execute();

        assertThat(Files.readString(outputFile)).contains("ModuleService");
        assertThat(Files.readString(manifestFile)).contains(
                "\"joernEnabled\": true",
                "\"joernFingerprint\": \"sha256:semantic\"");
        assertThat(Files.readString(checksumsFile)).contains("joern/cpg.bin");
    }

    @Test
    void analyzeAggregateGoalRunsReactorBtmGenerationAndSemanticImport(@TempDir Path tempDir) throws Exception {
        MavenProject root = project("root", "pom", tempDir);
        MavenProject module = project("module", "jar", tempDir.resolve("module"));
        Path moduleRoot = writeSource(tempDir.resolve("module/src/main/java"), "AggregateService");
        module.addCompileSourceRoot(moduleRoot.toString());
        MavenSession session = mock(MavenSession.class);
        when(session.getProjects()).thenReturn(List.of(root, module));
        Path outputFile = tempDir.resolve("target/forensics/aggregate.btm");
        Path manifestFile = tempDir.resolve("target/forensics/manifest.json");
        Path checksumsFile = tempDir.resolve("target/forensics/checksums.sha256");
        TestAnalyzeAggregateMojo mojo = new TestAnalyzeAggregateMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", root);
        setField(mojo, "session", session);
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "analysisStoreEnabled", true);
        setField(mojo, "analysisStoreDirectory", tempDir.resolve("target/forensics/analysis-store").toFile());
        setField(mojo, "cleanupPolicy", "KEEP_ON_SUCCESS");
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "checksumsFile", checksumsFile.toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 0);
        setField(mojo, "joernEnabled", true);

        mojo.execute();

        assertThat(Files.readString(outputFile)).contains("AggregateService");
        assertThat(Files.readString(manifestFile)).contains(
                "\"joernEnabled\": true",
                "\"joernFingerprint\": \"sha256:semantic\"");
        assertThat(Files.readString(checksumsFile)).contains("joern/cpg.bin");
    }

    private static ForensicsSemanticAnalysisRunner fakeSemanticRunner() {
        return new ForensicsSemanticAnalysisRunner(
                (config, checksumService) -> ignored -> new SemanticAnalysisResult(
                        "joern test",
                        "sha256:semantic",
                        List.of(new ArtifactChecksum("joern/cpg.bin", "joern-cpg", "abc", 3L)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()),
                H2AnalysisStoreAdapter::new);
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

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class TestAnalyzeMojo extends AnalyzeMojo {
        @Override
        protected ForensicsSemanticAnalysisRunner semanticRunner() {
            return fakeSemanticRunner();
        }
    }

    private static final class TestAnalyzeAggregateMojo extends AnalyzeAggregateMojo {
        @Override
        protected ForensicsSemanticAnalysisRunner semanticRunner() {
            return fakeSemanticRunner();
        }
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
