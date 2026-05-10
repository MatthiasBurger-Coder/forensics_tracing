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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MavenAnalysisStoreParityTest {

    @Test
    void btmgenWritesAnalysisStoreManifestChecksumsAndHeader(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = writeJavaSource(tempDir.resolve("src/main/java"));
        Path outputFile = tempDir.resolve("target/forensics/generated.btm");
        Path manifestFile = tempDir.resolve("target/forensics/manifest.json");
        Path checksumsFile = tempDir.resolve("target/forensics/checksums.sha256");
        Path analysisStoreDirectory = tempDir.resolve("target/forensics/analysis-store");
        Path database = analysisStoreDirectory.resolve("analysis-store");

        BtmGenMojo mojo = mojo(projectWithBuildDirectory(tempDir));
        setField(mojo, "sourceRoot", sourceRoot.toFile());
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "analysisStoreEnabled", true);
        setField(mojo, "analysisStoreDirectory", analysisStoreDirectory.toFile());
        setField(mojo, "cleanupPolicy", "KEEP_ON_SUCCESS");
        setField(mojo, "projectKey", "analysis-demo");
        setField(mojo, "pluginVersion", "1.0.0");
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "checksumsFile", checksumsFile.toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 0);

        mojo.execute();

        assertThat(outputFile).exists();
        assertThat(manifestFile).exists();
        assertThat(checksumsFile).exists();
        assertThat(database.resolveSibling(database.getFileName() + ".mv.db")).exists();

        String btm = Files.readString(outputFile);
        String manifest = Files.readString(manifestFile);
        String analysisRunId = headerValue(btm, "analysisRunId");
        assertThat(analysisRunId).isNotBlank();
        assertThat(btm).contains("# Forensics Analysis", "# projectKey: analysis-demo");
        assertThat(manifest).contains("\"analysisRunId\": \"" + analysisRunId + "\"");
        assertThat(Files.readString(checksumsFile)).contains("generated.btm", "manifest.json", "analysis-store/");

        assertThat(rowCount(database, "analysis_run")).isEqualTo(1);
        assertThat(rowCount(database, "source_file")).isEqualTo(1);
        assertThat(rowCount(database, "scan_event")).isGreaterThan(0);
        assertThat(rowCount(database, "btm_rule")).isGreaterThan(0);
    }

    @Test
    void analysisStoreCanBeDisabled(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = writeJavaSource(tempDir.resolve("src/main/java"));
        Path outputFile = tempDir.resolve("target/forensics/generated.btm");
        Path manifestFile = tempDir.resolve("target/forensics/manifest.json");
        Path checksumsFile = tempDir.resolve("target/forensics/checksums.sha256");
        Path analysisStoreDirectory = tempDir.resolve("target/forensics/analysis-store");
        BtmGenMojo mojo = mojo(projectWithBuildDirectory(tempDir));
        setField(mojo, "sourceRoot", sourceRoot.toFile());
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "analysisStoreEnabled", false);
        setField(mojo, "analysisStoreDirectory", analysisStoreDirectory.toFile());
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "checksumsFile", checksumsFile.toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 0);

        mojo.execute();

        assertThat(outputFile).exists();
        assertThat(Files.readString(outputFile)).doesNotContain("# Forensics Analysis");
        assertThat(manifestFile).doesNotExist();
        assertThat(checksumsFile).doesNotExist();
        assertThat(analysisStoreDirectory).doesNotExist();
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

    private static Path writeJavaSource(Path sourceRoot) throws Exception {
        Path packageDirectory = sourceRoot.resolve("com/example");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("DemoService.java"), """
                package com.example;
                public class DemoService {
                  public int run(int value) {
                    if (value > 0) { }
                    return value;
                  }
                }
                """);
        return sourceRoot;
    }

    private static String headerValue(String btm, String key) {
        var matcher = Pattern.compile("# " + key + ": ([^\\r\\n]+)").matcher(btm);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static long rowCount(Path databasePath, String tableName) throws SQLException {
        try (Connection connection = connect(databasePath);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private static Connection connect(Path databasePath) throws SQLException {
        return DriverManager.getConnection("jdbc:h2:file:"
                + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false");
    }

    private static void setField(BtmGenMojo mojo, String name, Object value) throws Exception {
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
