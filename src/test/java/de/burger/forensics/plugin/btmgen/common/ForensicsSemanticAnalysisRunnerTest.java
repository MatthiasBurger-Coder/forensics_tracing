package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.adapters.persistence.h2.H2AnalysisStoreAdapter;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;
import de.burger.forensics.domain.model.semantic.SemanticNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForensicsSemanticAnalysisRunnerTest {

    @Test
    void rejectsDisabledSemanticAnalysis(@TempDir Path tempDir) {
        ForensicsSemanticAnalysisRunner runner = new ForensicsSemanticAnalysisRunner();
        ForensicsSemanticAnalysisRequest request = request(tempDir, false, List.of(tempDir));

        assertThatThrownBy(() -> runner.analyze(request))
                .isInstanceOf(ForensicsSemanticAnalysisException.class)
                .hasMessageContaining("joernEnabled=true");
    }

    @Test
    void importsSemanticResultAndUpdatesArtifacts(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path forensicsDir = Files.createDirectories(tempDir.resolve("build/forensics"));
        Path analysisStoreDirectory = Files.createDirectories(forensicsDir.resolve("analysis-store"));
        Path database = analysisStoreDirectory.resolve("analysis-store");
        Path manifest = forensicsDir.resolve("manifest.json");
        Path checksums = forensicsDir.resolve("checksums.sha256");
        Path btmFile = Files.writeString(forensicsDir.resolve("generated.btm"), "RULE demo\nENDRULE\n");
        BuildIdentity identity = identity();
        writeManifest(manifest, identity);
        try (H2AnalysisStoreAdapter store = new H2AnalysisStoreAdapter(database)) {
            store.initializeSchema();
            store.createAnalysisRun(identity);
        }
        ForensicsSemanticAnalysisRunner runner = new ForensicsSemanticAnalysisRunner(
                (config, checksumService) -> ignored -> semanticResult(),
                H2AnalysisStoreAdapter::new);

        runner.analyze(new ForensicsSemanticAnalysisRequest(
                true,
                Path.of("joern"),
                Path.of("joern-parse"),
                Path.of("joern-slice"),
                forensicsDir.resolve("joern/workspace"),
                forensicsDir.resolve("joern"),
                "",
                30,
                true,
                List.of(sourceRoot),
                analysisStoreDirectory,
                manifest,
                checksums,
                btmFile));

        assertThat(Files.readString(manifest)).contains(
                "\"joernEnabled\": true",
                "\"joernFingerprint\": \"sha256:semantic\"");
        assertThat(Files.readString(checksums)).contains("joern/cpg.bin");
        assertThat(rowCount(database, "joern_import_run")).isEqualTo(1);
        assertThat(rowCount(database, "semantic_anchor")).isEqualTo(1);
    }

    @Test
    void requiresExistingSourceRootsWhenEnabled(@TempDir Path tempDir) throws Exception {
        Path forensicsDir = Files.createDirectories(tempDir.resolve("build/forensics"));
        Path manifest = forensicsDir.resolve("manifest.json");
        writeManifest(manifest, identity());
        ForensicsSemanticAnalysisRunner runner = new ForensicsSemanticAnalysisRunner(
                (config, checksumService) -> ignored -> semanticResult(),
                H2AnalysisStoreAdapter::new);
        ForensicsSemanticAnalysisRequest request = new ForensicsSemanticAnalysisRequest(
                true,
                Path.of("joern"),
                Path.of("joern-parse"),
                Path.of("joern-slice"),
                forensicsDir.resolve("joern/workspace"),
                forensicsDir.resolve("joern"),
                "",
                30,
                true,
                List.of(tempDir.resolve("missing")),
                forensicsDir.resolve("analysis-store"),
                manifest,
                forensicsDir.resolve("checksums.sha256"),
                forensicsDir.resolve("generated.btm"));

        assertThatThrownBy(() -> runner.analyze(request))
                .isInstanceOf(ForensicsSemanticAnalysisException.class)
                .hasMessageContaining("No source roots");
    }

    private static ForensicsSemanticAnalysisRequest request(Path tempDir, boolean joernEnabled, List<Path> sourceRoots) {
        return new ForensicsSemanticAnalysisRequest(
                joernEnabled,
                Path.of("joern"),
                Path.of("joern-parse"),
                Path.of("joern-slice"),
                tempDir.resolve("joern/workspace"),
                tempDir.resolve("joern"),
                "",
                300,
                true,
                sourceRoots,
                tempDir.resolve("analysis-store"),
                tempDir.resolve("manifest.json"),
                tempDir.resolve("checksums.sha256"),
                tempDir.resolve("generated.btm"));
    }

    private static SemanticAnalysisResult semanticResult() {
        return new SemanticAnalysisResult(
                "joern test",
                "sha256:semantic",
                List.of(new ArtifactChecksum("joern/cpg.bin", "joern-cpg", "abc", 3L)),
                List.of(new SemanticNode("n1", "CALL", "Demo.java", "demo.Demo", "run", "void run()", 12, "call()")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SemanticAnchor(
                        "demo.Demo#run:12:METHOD_ENTER",
                        "n1",
                        "Demo.java",
                        "demo.Demo",
                        "run",
                        "void run()",
                        12,
                        "call()",
                        0.95d,
                        "FQCN_METHOD_LINE_CODE")));
    }

    private static void writeManifest(Path manifest, BuildIdentity identity) throws Exception {
        Files.writeString(manifest, """
                {
                  "schemaVersion": "%s",
                  "projectKey": "%s",
                  "analysisRunId": "%s",
                  "buildId": "%s",
                  "sourceFingerprint": "%s",
                  "btmRulesFingerprint": "%s",
                  "pluginVersion": "%s",
                  "joernEnabled": false,
                  "createdAt": "%s",
                  "artifacts": []
                }
                """.formatted(
                identity.schemaVersion().value(),
                identity.projectKey(),
                identity.analysisRunId().value(),
                identity.buildId().value(),
                identity.sourceFingerprint().value(),
                identity.btmRulesFingerprint(),
                identity.pluginVersion(),
                identity.createdAt()));
    }

    private static BuildIdentity identity() {
        return new BuildIdentity(
                "demo",
                new AnalysisRunId("run-1"),
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                BuildIdentity.NOT_COMPUTED,
                "sha256:rules",
                BuildIdentity.NOT_COMPUTED,
                "test",
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);
    }

    private static long rowCount(Path databasePath, String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:file:"
                + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
