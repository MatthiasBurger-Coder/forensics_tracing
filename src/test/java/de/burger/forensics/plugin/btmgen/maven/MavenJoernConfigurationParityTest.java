package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisRequest;
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

class MavenJoernConfigurationParityTest {

    @Test
    void mapsMavenJoernParametersToSharedSemanticRequest(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src/main/java"));
        MavenProject project = projectWithBuildDirectory(tempDir);
        project.addCompileSourceRoot(sourceRoot.toString());
        MavenSemanticAnalysisParameters parameters = new MavenSemanticAnalysisParameters(
                project,
                null,
                false,
                true,
                tempDir.resolve("bin/joern").toString(),
                tempDir.resolve("bin/joern-parse").toString(),
                tempDir.resolve("bin/joern-slice").toString(),
                tempDir.resolve("work").toFile(),
                tempDir.resolve("out").toFile(),
                "-Xmx2g",
                120,
                false,
                tempDir.resolve("store").toFile(),
                tempDir.resolve("manifest.json").toFile(),
                tempDir.resolve("checksums.sha256").toFile(),
                tempDir.resolve("generated.btm").toFile());

        ForensicsSemanticAnalysisRequest request = parameters.toAnalysisRequest();

        assertThat(request.joernEnabled()).isTrue();
        assertThat(request.joernExecutable()).isEqualTo(tempDir.resolve("bin/joern").toAbsolutePath());
        assertThat(request.joernParseExecutable()).isEqualTo(tempDir.resolve("bin/joern-parse").toAbsolutePath());
        assertThat(request.joernSliceExecutable()).isEqualTo(tempDir.resolve("bin/joern-slice").toAbsolutePath());
        assertThat(request.joernWorkspaceDirectory()).isEqualTo(tempDir.resolve("work").toAbsolutePath());
        assertThat(request.joernOutputDirectory()).isEqualTo(tempDir.resolve("out").toAbsolutePath());
        assertThat(request.joernMaxHeap()).isEqualTo("-Xmx2g");
        assertThat(request.joernTimeoutSeconds()).isEqualTo(120);
        assertThat(request.joernFailOnError()).isFalse();
        assertThat(request.sourceRoots()).containsExactly(sourceRoot.toAbsolutePath());
        assertThat(request.analysisStoreDirectory()).isEqualTo(tempDir.resolve("store").toAbsolutePath());
        assertThat(request.manifestFile()).isEqualTo(tempDir.resolve("manifest.json").toAbsolutePath());
        assertThat(request.checksumsFile()).isEqualTo(tempDir.resolve("checksums.sha256").toAbsolutePath());
        assertThat(request.outputFile()).isEqualTo(tempDir.resolve("generated.btm").toAbsolutePath());
    }

    @Test
    void mapsMavenJoernDefaultsToPathCommands(@TempDir Path tempDir) {
        MavenSemanticAnalysisParameters parameters = new MavenSemanticAnalysisParameters(
                projectWithBuildDirectory(tempDir),
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                300,
                true,
                null,
                null,
                null,
                null);

        ForensicsSemanticAnalysisRequest request = parameters.toAnalysisRequest(List.of(tempDir));

        assertThat(request.joernEnabled()).isFalse();
        assertThat(request.joernExecutable()).isEqualTo(Path.of("joern"));
        assertThat(request.joernParseExecutable()).isEqualTo(Path.of("joern-parse"));
        assertThat(request.joernSliceExecutable()).isEqualTo(Path.of("joern-slice"));
        assertThat(request.joernWorkspaceDirectory()).isEqualTo(tempDir.resolve("target/forensics/joern/workspace").toAbsolutePath());
        assertThat(request.joernOutputDirectory()).isEqualTo(tempDir.resolve("target/forensics/joern").toAbsolutePath());
        assertThat(request.joernMaxHeap()).isEmpty();
        assertThat(request.joernTimeoutSeconds()).isEqualTo(300);
        assertThat(request.joernFailOnError()).isTrue();
    }

    @Test
    void mapsRelativeMavenJoernExecutablePathsAgainstProjectBaseDirectory(@TempDir Path tempDir) {
        MavenSemanticAnalysisParameters parameters = new MavenSemanticAnalysisParameters(
                projectWithBuildDirectory(tempDir),
                null,
                false,
                true,
                "tools/joern",
                "tools/joern-parse",
                "tools/joern-slice",
                null,
                null,
                null,
                300,
                true,
                null,
                null,
                null,
                null);

        ForensicsSemanticAnalysisRequest request = parameters.toAnalysisRequest(List.of(tempDir));

        assertThat(request.joernExecutable()).isEqualTo(tempDir.resolve("tools/joern").toAbsolutePath());
        assertThat(request.joernParseExecutable()).isEqualTo(tempDir.resolve("tools/joern-parse").toAbsolutePath());
        assertThat(request.joernSliceExecutable()).isEqualTo(tempDir.resolve("tools/joern-slice").toAbsolutePath());
    }

    @Test
    void importGoalUsesMavenJoernEnablementHint(@TempDir Path tempDir) throws Exception {
        ImportSemanticsMojo mojo = new ImportSemanticsMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", projectWithBuildDirectory(tempDir));

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("forensics.joernEnabled=true");
    }

    @Test
    void importGoalAcceptsExistingCallgraphArtifact(@TempDir Path tempDir) throws Exception {
        Path joernOutput = Files.createDirectories(tempDir.resolve("target/forensics/joern"));
        Files.writeString(joernOutput.resolve("callgraph.json"), "{}");
        ImportSemanticsMojo mojo = new ImportSemanticsMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", projectWithBuildDirectory(tempDir));
        setField(mojo, "joernEnabled", true);
        setField(mojo, "joernOutputDirectory", joernOutput.toFile());

        mojo.execute();

        assertThat(joernOutput.resolve("callgraph.json")).isRegularFile();
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

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
