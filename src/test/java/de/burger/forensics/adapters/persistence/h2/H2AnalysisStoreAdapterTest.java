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

    private static Connection connect(Path databasePath) throws SQLException {
        return DriverManager.getConnection("jdbc:h2:file:"
                + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false");
    }
}
