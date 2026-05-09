package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateBtmTaskAnalysisStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void generateBtmRulesWritesAnalysisStoreManifestChecksumsAndHeader() throws IOException, SQLException {
        writeSettings(tempDir);
        Files.writeString(tempDir.resolve("build.gradle.kts"), buildScript("""
                btmGen {
                    minBranchesPerMethod.set(0)
                    projectKey.set("analysis-demo")
                }
                """));
        writeJavaSource(tempDir.resolve("src/main/java"));

        BuildResult result = runGradle(tempDir, ":generateBtmRules");

        assertThat(result.task(":generateBtmRules")).isNotNull();
        assertThat(result.task(":generateBtmRules").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        Path btmFile = tempDir.resolve("build/forensics/forensics.btm");
        Path manifestFile = tempDir.resolve("build/forensics/manifest.json");
        Path checksumsFile = tempDir.resolve("build/forensics/checksums.sha256");
        Path database = tempDir.resolve("build/forensics/analysis-store/analysis-store");

        assertThat(btmFile).exists();
        assertThat(manifestFile).exists();
        assertThat(checksumsFile).exists();
        assertThat(database.resolveSibling(database.getFileName() + ".mv.db")).exists();

        String btm = Files.readString(btmFile);
        String manifest = Files.readString(manifestFile);
        String analysisRunId = headerValue(btm, "analysisRunId");
        assertThat(analysisRunId).isNotBlank();
        assertThat(btm).contains("# Forensics Analysis", "# projectKey: analysis-demo");
        assertThat(manifest).contains("\"analysisRunId\": \"" + analysisRunId + "\"");
        assertThat(Files.readString(checksumsFile)).contains("forensics.btm", "manifest.json", "analysis-store/");

        assertThat(rowCount(database, "analysis_run")).isEqualTo(1);
        assertThat(rowCount(database, "source_file")).isEqualTo(1);
        assertThat(rowCount(database, "scan_event")).isGreaterThan(0);
        assertThat(rowCount(database, "btm_rule")).isGreaterThan(0);
    }

    @Test
    void analysisStoreCanBeDisabled() throws IOException {
        writeSettings(tempDir);
        Files.writeString(tempDir.resolve("build.gradle.kts"), buildScript("""
                btmGen {
                    analysisStoreEnabled.set(false)
                    minBranchesPerMethod.set(0)
                }
                """));
        writeJavaSource(tempDir.resolve("src/main/java"));

        BuildResult result = runGradle(tempDir, ":generateBtmRules");

        assertThat(result.task(":generateBtmRules").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        String btm = Files.readString(tempDir.resolve("build/forensics/forensics.btm"));
        assertThat(btm).doesNotContain("# Forensics Analysis");
        assertThat(tempDir.resolve("build/forensics/manifest.json")).doesNotExist();
        assertThat(tempDir.resolve("build/forensics/checksums.sha256")).doesNotExist();
        Path database = tempDir.resolve("build/forensics/analysis-store/analysis-store");
        assertThat(database.resolveSibling(database.getFileName() + ".mv.db")).doesNotExist();
    }

    private static void writeSettings(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), """
                rootProject.name = "analysis-store-functional"
                """);
    }

    private static String buildScript(String configuration) {
        return """
                plugins {
                    java
                    id("de.burger.forensics.btmgen")
                }

                java {
                    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
                }

                %s
                """.formatted(configuration);
    }

    private static void writeJavaSource(Path sourceRoot) throws IOException {
        Path packageDir = sourceRoot.resolve("com/example");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("DemoService.java"), """
                package com.example;
                public class DemoService {
                  public int run(int value) {
                    if (value > 0) { }
                    return value;
                  }
                }
                """);
    }

    private static BuildResult runGradle(Path projectDir, String... arguments) {
        List<String> gradleArguments = new ArrayList<>();
        gradleArguments.add("--stacktrace");
        gradleArguments.addAll(List.of(arguments));
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(gradleArguments)
                .build();
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
}
