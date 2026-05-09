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
import de.burger.forensics.domain.model.semantic.CallRelation;
import de.burger.forensics.domain.model.semantic.ControlFlowRelation;
import de.burger.forensics.domain.model.semantic.DataFlowPath;
import de.burger.forensics.domain.model.semantic.DataFlowStep;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;
import de.burger.forensics.domain.model.semantic.SemanticEdge;
import de.burger.forensics.domain.model.semantic.SemanticMethod;
import de.burger.forensics.domain.model.semantic.SemanticNode;
import de.burger.forensics.domain.port.out.AnalysisStorePort;
import de.burger.forensics.domain.port.out.SemanticAnalysisStorePort;

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
public final class H2AnalysisStoreAdapter implements AnalysisStorePort, SemanticAnalysisStorePort {

    private static final String ANALYSIS_RUN_ID_REQUIRED = "Analysis run id must not be null.";
    private static final String INSERT_ANALYSIS_RUN_SQL = """
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
            """;
    private static final String UPDATE_ANALYSIS_RUN_STATUS_SQL = """
            UPDATE analysis_run
            SET status = ?
            WHERE analysis_run_id = ?
            """;
    private static final String INSERT_SOURCE_FILE_SQL = """
            INSERT INTO source_file (
                analysis_run_id,
                relative_path,
                absolute_path,
                sha256,
                file_size,
                last_modified_epoch_millis
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_SCAN_METHOD_SQL = """
            INSERT INTO scan_method (
                analysis_run_id,
                method_key,
                fqcn,
                method_name,
                signature,
                return_type
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_SCAN_EVENT_SQL = """
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
            """;
    private static final String INSERT_BTM_RULE_SQL = """
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
            """;
    private static final String INSERT_ARTIFACT_CHECKSUM_SQL = """
            INSERT INTO artifact_checksum (
                analysis_run_id,
                artifact_path,
                artifact_type,
                sha256,
                size_bytes
            )
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_JOERN_IMPORT_RUN_SQL = """
            INSERT INTO joern_import_run (
                analysis_run_id,
                joern_fingerprint,
                joern_version,
                status,
                started_at
            )
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_SEMANTIC_ANCHOR_SQL = """
            INSERT INTO semantic_anchor (
                analysis_run_id,
                scan_event_key,
                semantic_node_id,
                relative_path,
                fqcn,
                method_name,
                signature,
                line_number,
                normalized_code,
                confidence,
                match_strategy
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_JOERN_IMPORT_RUN_STATUS_SQL = """
            UPDATE joern_import_run
            SET status = ?,
                completed_at = ?
            WHERE analysis_run_id = ?
              AND joern_fingerprint = ?
            """;

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
            try (PreparedStatement statement = connection.prepareStatement(INSERT_ANALYSIS_RUN_SQL)) {
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
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(status, "Analysis run status must not be null.");
        transactions.run("update analysis run status", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_ANALYSIS_RUN_STATUS_SQL)) {
                statement.setString(1, status.name());
                statement.setString(2, analysisRunId.value());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void storeSourceFiles(AnalysisRunId analysisRunId, List<SourceFileSnapshot> sourceFiles) {
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(sourceFiles, "Source files must not be null.");
        transactions.run("store source files", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SOURCE_FILE_SQL)) {
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
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(methods, "Methods must not be null.");
        transactions.run("store methods", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SCAN_METHOD_SQL)) {
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
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(events, "Scan events must not be null.");
        transactions.run("store scan events", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SCAN_EVENT_SQL)) {
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
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(rules, "Rules must not be null.");
        Objects.requireNonNull(renderedRulesByRuleId, "Rendered rules must not be null.");
        transactions.run("store rules", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_BTM_RULE_SQL)) {
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
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(checksums, "Artifact checksums must not be null.");
        transactions.run("store artifact checksums", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_ARTIFACT_CHECKSUM_SQL)) {
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
    public void createSemanticImportRun(AnalysisRunId analysisRunId, SemanticAnalysisResult result) {
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(result, "Semantic analysis result must not be null.");
        transactions.run("create semantic import run", connection -> {
            deleteSemanticData(connection, analysisRunId);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_JOERN_IMPORT_RUN_SQL)) {
                statement.setString(1, analysisRunId.value());
                statement.setString(2, result.semanticFingerprint());
                statement.setString(3, result.providerVersion());
                statement.setString(4, "STARTED");
                statement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void storeSemanticGraph(AnalysisRunId analysisRunId, SemanticAnalysisResult result) {
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(result, "Semantic analysis result must not be null.");
        transactions.run("store semantic graph", connection -> {
            insertSemanticNodes(connection, analysisRunId, result.nodes());
            insertSemanticEdges(connection, analysisRunId, result.edges());
            insertSemanticMethods(connection, analysisRunId, result.methods());
            insertCallRelations(connection, analysisRunId, result.callRelations());
            insertControlFlowRelations(connection, analysisRunId, result.controlFlowRelations());
            insertDataFlowPaths(connection, analysisRunId, result.dataFlowPaths());
        });
    }

    @Override
    public void storeSemanticAnchors(AnalysisRunId analysisRunId, List<SemanticAnchor> anchors) {
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        Objects.requireNonNull(anchors, "Semantic anchors must not be null.");
        transactions.run("store semantic anchors", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SEMANTIC_ANCHOR_SQL)) {
                for (SemanticAnchor anchor : anchors) {
                    statement.setString(1, analysisRunId.value());
                    statement.setString(2, anchor.scanEventKey());
                    statement.setString(3, anchor.semanticNodeId());
                    statement.setString(4, anchor.relativePath());
                    setNullableString(statement, 5, anchor.fqcn());
                    statement.setString(6, anchor.methodName());
                    setNullableString(statement, 7, anchor.signature());
                    statement.setInt(8, anchor.lineNumber());
                    setNullableString(statement, 9, anchor.normalizedCode());
                    statement.setDouble(10, anchor.confidence());
                    statement.setString(11, anchor.matchStrategy());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    @Override
    public void updateSemanticImportStatus(
            AnalysisRunId analysisRunId,
            String semanticFingerprint,
            String status
    ) {
        Objects.requireNonNull(analysisRunId, ANALYSIS_RUN_ID_REQUIRED);
        if (semanticFingerprint == null || semanticFingerprint.isBlank()) {
            throw new IllegalArgumentException("Semantic fingerprint must not be blank.");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Semantic import status must not be blank.");
        }
        transactions.run("update semantic import status", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_JOERN_IMPORT_RUN_STATUS_SQL)) {
                statement.setString(1, status);
                statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                statement.setString(3, analysisRunId.value());
                statement.setString(4, semanticFingerprint);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void close() {
        // Connections are scoped per transaction.
    }

    private static void deleteSemanticData(Connection connection, AnalysisRunId analysisRunId) throws SQLException {
        List<String> statements = List.of(
                "DELETE FROM semantic_anchor WHERE analysis_run_id = ?",
                "DELETE FROM joern_data_flow_step WHERE analysis_run_id = ?",
                "DELETE FROM joern_data_flow_path WHERE analysis_run_id = ?",
                "DELETE FROM joern_control_flow_relation WHERE analysis_run_id = ?",
                "DELETE FROM joern_call_relation WHERE analysis_run_id = ?",
                "DELETE FROM joern_method WHERE analysis_run_id = ?",
                "DELETE FROM joern_edge WHERE analysis_run_id = ?",
                "DELETE FROM joern_node WHERE analysis_run_id = ?",
                "DELETE FROM joern_import_run WHERE analysis_run_id = ?");
        for (String sql : statements) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, analysisRunId.value());
                statement.executeUpdate();
            }
        }
    }

    private static void insertSemanticNodes(
            Connection connection,
            AnalysisRunId analysisRunId,
            List<SemanticNode> nodes
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO joern_node (
                    analysis_run_id,
                    node_id,
                    node_type,
                    relative_path,
                    fqcn,
                    method_name,
                    signature,
                    line_number,
                    normalized_code
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (SemanticNode node : nodes) {
                statement.setString(1, analysisRunId.value());
                statement.setString(2, node.nodeId());
                statement.setString(3, node.nodeType());
                statement.setString(4, node.relativePath());
                setNullableString(statement, 5, node.fqcn());
                statement.setString(6, node.methodName());
                setNullableString(statement, 7, node.signature());
                statement.setInt(8, node.lineNumber());
                setNullableString(statement, 9, node.normalizedCode());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertSemanticEdges(
            Connection connection,
            AnalysisRunId analysisRunId,
            List<SemanticEdge> edges
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO joern_edge (
                    analysis_run_id,
                    edge_id,
                    source_node_id,
                    target_node_id,
                    edge_type
                )
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (SemanticEdge edge : edges) {
                statement.setString(1, analysisRunId.value());
                statement.setString(2, edge.edgeId());
                statement.setString(3, edge.sourceNodeId());
                statement.setString(4, edge.targetNodeId());
                statement.setString(5, edge.edgeType());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertSemanticMethods(
            Connection connection,
            AnalysisRunId analysisRunId,
            List<SemanticMethod> methods
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO joern_method (
                    analysis_run_id,
                    method_id,
                    relative_path,
                    fqcn,
                    method_name,
                    signature,
                    line_number
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (SemanticMethod method : methods) {
                statement.setString(1, analysisRunId.value());
                statement.setString(2, method.methodId());
                statement.setString(3, method.relativePath());
                statement.setString(4, method.fqcn());
                statement.setString(5, method.methodName());
                setNullableString(statement, 6, method.signature());
                statement.setInt(7, method.lineNumber());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertCallRelations(
            Connection connection,
            AnalysisRunId analysisRunId,
            List<CallRelation> relations
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO joern_call_relation (
                    analysis_run_id,
                    caller_method_id,
                    callee_method_id,
                    call_node_id
                )
                VALUES (?, ?, ?, ?)
                """)) {
            for (CallRelation relation : relations) {
                statement.setString(1, analysisRunId.value());
                statement.setString(2, relation.callerMethodId());
                statement.setString(3, relation.calleeMethodId());
                statement.setString(4, relation.callNodeId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertControlFlowRelations(
            Connection connection,
            AnalysisRunId analysisRunId,
            List<ControlFlowRelation> relations
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO joern_control_flow_relation (
                    analysis_run_id,
                    source_node_id,
                    target_node_id,
                    relation_type
                )
                VALUES (?, ?, ?, ?)
                """)) {
            for (ControlFlowRelation relation : relations) {
                statement.setString(1, analysisRunId.value());
                statement.setString(2, relation.sourceNodeId());
                statement.setString(3, relation.targetNodeId());
                statement.setString(4, relation.relationType());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertDataFlowPaths(
            Connection connection,
            AnalysisRunId analysisRunId,
            List<DataFlowPath> paths
    ) throws SQLException {
        try (PreparedStatement pathStatement = connection.prepareStatement("""
                INSERT INTO joern_data_flow_path (
                    analysis_run_id,
                    path_id,
                    source_node_id,
                    target_node_id
                )
                VALUES (?, ?, ?, ?)
                """);
             PreparedStatement stepStatement = connection.prepareStatement("""
                INSERT INTO joern_data_flow_step (
                    analysis_run_id,
                    path_id,
                    node_id,
                    step_order,
                    step_kind
                )
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (DataFlowPath path : paths) {
                pathStatement.setString(1, analysisRunId.value());
                pathStatement.setString(2, path.pathId());
                pathStatement.setString(3, path.sourceNodeId());
                pathStatement.setString(4, path.targetNodeId());
                pathStatement.addBatch();
                for (DataFlowStep step : path.steps()) {
                    stepStatement.setString(1, analysisRunId.value());
                    stepStatement.setString(2, path.pathId());
                    stepStatement.setString(3, step.nodeId());
                    stepStatement.setInt(4, step.orderIndex());
                    stepStatement.setString(5, step.kind());
                    stepStatement.addBatch();
                }
            }
            pathStatement.executeBatch();
            stepStatement.executeBatch();
        }
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
