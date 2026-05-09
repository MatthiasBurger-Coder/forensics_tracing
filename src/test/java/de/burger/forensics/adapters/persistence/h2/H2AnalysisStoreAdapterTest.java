package de.burger.forensics.adapters.persistence.h2;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.RuleId;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisRunStatus;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFileSnapshot;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import de.burger.forensics.domain.model.entry.MethodEntry;
import de.burger.forensics.domain.model.semantic.CallRelation;
import de.burger.forensics.domain.model.semantic.ControlFlowRelation;
import de.burger.forensics.domain.model.semantic.DataFlowPath;
import de.burger.forensics.domain.model.semantic.DataFlowStep;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;
import de.burger.forensics.domain.model.semantic.SemanticEdge;
import de.burger.forensics.domain.model.semantic.SemanticMethod;
import de.burger.forensics.domain.model.semantic.SemanticNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class H2AnalysisStoreAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAnalysisRunEventsRulesStatusAndChecksums() throws SQLException {
        Path database = tempDir.resolve("analysis-store");
        AnalysisRunId runId = new AnalysisRunId("run-1");
        H2AnalysisStoreAdapter adapter = new H2AnalysisStoreAdapter(database);
        Rule rule = new Rule(
                new RuleId("rule-1"),
                new SourceLocation("com.example.Demo", "run", 12),
                "true",
                true,
                "Helper",
                RuleTemplate.METHOD_ENTER,
                "void run()",
                "void");

        adapter.initializeSchema();
        adapter.createAnalysisRun(identity(runId));
        adapter.updateAnalysisRunStatus(runId, AnalysisRunStatus.SCANNING);
        adapter.storeSourceFiles(runId, List.of(new SourceFileSnapshot(
                "com/example/Demo.java",
                tempDir.resolve("src/com/example/Demo.java").toString(),
                "abc123",
                17L,
                1L)));
        adapter.storeMethods(runId, List.of(new MethodEntry(
                "com.example.Demo#run::void run()",
                "Demo",
                "run",
                List.of(),
                "void")));
        adapter.storeScanEvents(runId, List.of(new ScanEvent(
                new SourceLocation("com.example.Demo", "run", 12),
                "void run()",
                RuleTemplate.METHOD_ENTER,
                null,
                "java",
                "void")));
        adapter.storeRules(runId, List.of(rule), Map.of("rule-1", "RULE rule-1\nENDRULE"));
        adapter.storeArtifactChecksums(runId, List.of(new ArtifactChecksum("forensics.btm", "byteman-rules", "def456", 42L)));
        adapter.updateAnalysisRunStatus(runId, AnalysisRunStatus.COMPLETED);
        adapter.close();

        assertThat(rowCount(database, "analysis_run")).isEqualTo(1);
        assertThat(rowCount(database, "source_file")).isEqualTo(1);
        assertThat(rowCount(database, "scan_method")).isEqualTo(1);
        assertThat(rowCount(database, "scan_event")).isEqualTo(1);
        assertThat(rowCount(database, "btm_rule")).isEqualTo(1);
        assertThat(rowCount(database, "artifact_checksum")).isEqualTo(1);
        assertThat(status(database)).isEqualTo("COMPLETED");
    }

    @Test
    void storesSemanticImportGraphAndAnchorsIdempotently() throws SQLException {
        Path database = tempDir.resolve("semantic-store");
        AnalysisRunId runId = new AnalysisRunId("run-semantic");
        H2AnalysisStoreAdapter adapter = new H2AnalysisStoreAdapter(database);
        SemanticAnalysisResult result = semanticResult();

        adapter.initializeSchema();
        adapter.createAnalysisRun(identity(runId));
        adapter.createSemanticImportRun(runId, result);
        adapter.storeSemanticGraph(runId, result);
        adapter.storeSemanticAnchors(runId, result.anchors());
        adapter.storeArtifactChecksums(runId, result.artifacts());
        adapter.updateSemanticImportStatus(runId, result.semanticFingerprint(), "COMPLETED");

        adapter.createSemanticImportRun(runId, result);
        adapter.storeSemanticGraph(runId, result);
        adapter.storeSemanticAnchors(runId, result.anchors());
        adapter.updateSemanticImportStatus(runId, result.semanticFingerprint(), "COMPLETED");
        adapter.close();

        assertThat(rowCount(database, "joern_import_run")).isEqualTo(1);
        assertThat(rowCount(database, "joern_node")).isEqualTo(1);
        assertThat(rowCount(database, "joern_edge")).isEqualTo(1);
        assertThat(rowCount(database, "joern_method")).isEqualTo(1);
        assertThat(rowCount(database, "joern_call_relation")).isEqualTo(1);
        assertThat(rowCount(database, "joern_control_flow_relation")).isEqualTo(1);
        assertThat(rowCount(database, "joern_data_flow_path")).isEqualTo(1);
        assertThat(rowCount(database, "joern_data_flow_step")).isEqualTo(1);
        assertThat(rowCount(database, "semantic_anchor")).isEqualTo(1);
        assertThat(semanticStatus(database)).isEqualTo("COMPLETED");
    }

    private static BuildIdentity identity(AnalysisRunId runId) {
        return new BuildIdentity(
                "demo",
                runId,
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                BuildIdentity.NOT_COMPUTED,
                "sha256:rules",
                BuildIdentity.NOT_COMPUTED,
                "test",
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);
    }

    private static SemanticAnalysisResult semanticResult() {
        return new SemanticAnalysisResult(
                "joern 1.0",
                "sha256:semantic",
                List.of(new ArtifactChecksum("joern/cpg.bin", "joern-cpg", "abc", 3L)),
                List.of(new SemanticNode("n1", "CALL", "Demo.java", "demo.Demo", "run", "void run()", 12, "call()")),
                List.of(new SemanticEdge("e1", "n1", "n2", "CALL")),
                List.of(new SemanticMethod("m1", "Demo.java", "demo.Demo", "run", "void run()", 12)),
                List.of(new CallRelation("m1", "m2", "n1")),
                List.of(new ControlFlowRelation("n1", "n2", "NEXT")),
                List.of(new DataFlowPath("p1", "n1", "n2", List.of(new DataFlowStep("n1", 0, "SOURCE")))),
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

    private static long rowCount(Path databasePath, String tableName) throws SQLException {
        try (Connection connection = connect(databasePath);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private static String status(Path databasePath) throws SQLException {
        try (Connection connection = connect(databasePath);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT status FROM analysis_run")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString("status");
        }
    }

    private static String semanticStatus(Path databasePath) throws SQLException {
        try (Connection connection = connect(databasePath);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT status FROM joern_import_run")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString("status");
        }
    }

    private static Connection connect(Path databasePath) throws SQLException {
        return DriverManager.getConnection("jdbc:h2:file:"
                + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false");
    }
}
