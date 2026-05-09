package de.burger.forensics.plugin.btmgen.maven;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        mojo.setLog(new SilentLog());
        setField(mojo, "project", project);
        return mojo;
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
        return createSource(sourceRoot, "com.example", "Sample");
    }

    private static Path createSource(Path sourceRoot, String packageName, String className) throws Exception {
        Path packageDirectory = sourceRoot.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve(className + ".java"), """
                package %s;
                public class %s {
                  public int run(int value) {
                    if (value > 0) { }
                    return value;
                  }
                }
                """.formatted(packageName, className));
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
