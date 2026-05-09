package de.burger.forensics.adapters.persistence.h2;

import de.burger.forensics.adaptersupport.persistence.h2.H2ConnectionFactory;
import de.burger.forensics.adaptersupport.persistence.h2.SqlTransactionRunner;
import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisRunStatus;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFileSnapshot;
import de.burger.forensics.domain.model.entry.MethodEntry;
import de.burger.forensics.domain.port.out.AnalysisStorePort;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * H2-backed storage adapter for persisted forensics analysis snapshots.
 */
public final class H2AnalysisStoreAdapter implements AnalysisStorePort {

    private final Path databasePath;
    private final SqlTransactionRunner transactions;

    public H2AnalysisStoreAdapter(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "Database path must not be null.");
        this.transactions = new SqlTransactionRunner(new H2ConnectionFactory(databasePath));
    }

    @Override
    public void initializeSchema() {
        new H2SchemaInitializer(databasePath).initialize();
    }

    @Override
    public void createAnalysisRun(BuildIdentity identity) {
        Objects.requireNonNull(identity, "Build identity must not be null.");
        transactions.run("create analysis run", connection -> {
            deleteExistingRun(connection, identity.analysisRunId());
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO analysis_run (
                        analysis_run_id,
                        project_key,
                        build_id,
                        source_fingerprint,
                        classpath_fingerprint,
                        btm_rules_fingerprint,
                        artifact_fingerprint,
                        plugin_version,
                        schema_version,
                        status,
                        created_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, identity.analysisRunId().value());
                statement.setString(2, identity.projectKey());
                statement.setString(3, identity.buildId().value());
                statement.setString(4, identity.sourceFingerprint().value());
                statement.setString(5, identity.classpathFingerprint());
                statement.setString(6, identity.btmRulesFingerprint());
                statement.setString(7, identity.artifactFingerprint());
                statement.setString(8, identity.pluginVersion());
                statement.setString(9, identity.schemaVersion().value());
                statement.setString(10, AnalysisRunStatus.CREATED.name());
                Timestamp createdAt = Timestamp.from(identity.createdAt());
                statement.setTimestamp(11, createdAt);
                statement.setTimestamp(12, createdAt);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void updateAnalysisRunStatus(AnalysisRunId analysisRunId, AnalysisRunStatus status) {
        Objects.requireNonNull(analysisRunId, "Analysis run id must not be null.");
        Objects.requireNonNull(status, "Analysis run status must not be null.");
        transactions.run("update analysis run status", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE analysis_run
                    SET status = ?
                    WHERE analysis_run_id = ?
                    """)) {
                statement.setString(1, status.name());
                statement.setString(2, analysisRunId.value());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void storeSourceFiles(AnalysisRunId analysisRunId, List<SourceFileSnapshot> sourceFiles) {
        Objects.requireNonNull(analysisRunId, "Analysis run id must not be null.");
        Objects.requireNonNull(sourceFiles, "Source files must not be null.");
        transactions.run("store source files", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO source_file (
                        analysis_run_id,
                        relative_path,
                        absolute_path,
                        sha256,
                        file_size,
                        last_modified_epoch_millis
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                for (SourceFileSnapshot sourceFile : sourceFiles) {
                    statement.setString(1, analysisRunId.value());
                    statement.setString(2, sourceFile.relativePath());
                    statement.setString(3, sourceFile.absolutePath());
                    statement.setString(4, sourceFile.sha256());
                    statement.setLong(5, sourceFile.fileSize());
                    statement.setLong(6, sourceFile.lastModifiedEpochMillis());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public void storeMethods(AnalysisRunId analysisRunId, List<MethodEntry> methods) {
        Objects.requireNonNull(analysisRunId, "Analysis run id must not be null.");
        Objects.requireNonNull(methods, "Methods must not be null.");
        transactions.run("store methods", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO scan_method (
                        analysis_run_id,
                        method_key,
                        fqcn,
                        method_name,
                        signature,
                        return_type
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                for (MethodEntry method : methods) {
                    statement.setString(1, analysisRunId.value());
                    statement.setString(2, method.fullyQualifiedMethodName());
                    statement.setString(3, fqcnFromMethodKey(method.fullyQualifiedMethodName(), method.className()));
                    statement.setString(4, method.methodName());
                    setNullableString(statement, 5, signature(method));
                    setNullableString(statement, 6, method.returnType());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public void storeScanEvents(AnalysisRunId analysisRunId, List<ScanEvent> events) {
        Objects.requireNonNull(analysisRunId, "Analysis run id must not be null.");
        Objects.requireNonNull(events, "Scan events must not be null.");
        transactions.run("store scan events", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO scan_event (
                        analysis_run_id,
                        fqcn,
                        method_name,
                        signature,
                        rule_template,
                        line_number,
                        condition_text,
                        language,
                        return_type
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (ScanEvent event : events) {
                    SourceLocation location = event.location();
                    statement.setString(1, analysisRunId.value());
                    statement.setString(2, location.fqcn());
                    statement.setString(3, location.method());
                    setNullableString(statement, 4, event.signature());
                    statement.setString(5, event.kind().name());
                    statement.setInt(6, location.line());
                    setNullableString(statement, 7, event.conditionText());
                    setNullableString(statement, 8, event.language());
                    setNullableString(statement, 9, event.returnType());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public void storeRules(AnalysisRunId analysisRunId, List<Rule> rules, Map<String, String> renderedRulesByRuleId) {
        Objects.requireNonNull(analysisRunId, "Analysis run id must not be null.");
        Objects.requireNonNull(rules, "Rules must not be null.");
        Objects.requireNonNull(renderedRulesByRuleId, "Rendered rules must not be null.");
        transactions.run("store rules", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO btm_rule (
                        analysis_run_id,
                        rule_id,
                        fqcn,
                        method_name,
                        rule_template,
                        line_number,
                        rendered_rule,
                        emitted_to_btm
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (Rule rule : rules) {
                    SourceLocation location = rule.location();
                    String renderedRule = renderedRulesByRuleId.get(rule.id().value());
                    statement.setString(1, analysisRunId.value());
                    statement.setString(2, rule.id().value());
                    statement.setString(3, location.fqcn());
                    statement.setString(4, location.method());
                    statement.setString(5, rule.type().name());
                    statement.setInt(6, location.line());
                    statement.setString(7, renderedRule == null ? "" : renderedRule);
                    statement.setBoolean(8, renderedRule != null);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public void storeArtifactChecksums(AnalysisRunId analysisRunId, List<ArtifactChecksum> checksums) {
        Objects.requireNonNull(analysisRunId, "Analysis run id must not be null.");
        Objects.requireNonNull(checksums, "Artifact checksums must not be null.");
        transactions.run("store artifact checksums", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO artifact_checksum (
                        analysis_run_id,
                        artifact_path,
                        artifact_type,
                        sha256,
                        size_bytes
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                for (ArtifactChecksum checksum : checksums) {
                    statement.setString(1, analysisRunId.value());
                    statement.setString(2, checksum.path());
                    statement.setString(3, checksum.type());
                    statement.setString(4, checksum.sha256());
                    statement.setLong(5, checksum.sizeBytes());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public void close() {
        // Connections are scoped per transaction.
    }

    private static void deleteExistingRun(Connection connection, AnalysisRunId analysisRunId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM analysis_run
                WHERE analysis_run_id = ?
                """)) {
            statement.setString(1, analysisRunId.value());
            statement.executeUpdate();
        }
    }

    private static String fqcnFromMethodKey(String methodKey, String fallbackClassName) {
        int separator = methodKey.indexOf('#');
        if (separator > 0) {
            return methodKey.substring(0, separator);
        }
        return fallbackClassName;
    }

    private static String signature(MethodEntry method) {
        return "(" + String.join(",", method.parameterTypes()) + ")";
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }
}
