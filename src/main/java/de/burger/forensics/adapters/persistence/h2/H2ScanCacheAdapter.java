package de.burger.forensics.adapters.persistence.h2;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.cache.CachedScanResult;
import de.burger.forensics.domain.model.cache.DependencyKind;
import de.burger.forensics.domain.model.cache.ScanDependency;
import de.burger.forensics.domain.model.cache.ScanPhase;
import de.burger.forensics.domain.model.cache.ScanProfile;
import de.burger.forensics.domain.model.cache.SourceFileFingerprint;
import de.burger.forensics.domain.model.cache.SourceFileSnapshot;
import de.burger.forensics.domain.port.out.ScanCachePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * H2-backed implementation of the parser scan cache port.
 */
public final class H2ScanCacheAdapter implements ScanCachePort {

    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final String PHASE_METRIC_PREFIX = "phase:";

    private final Path databasePath;
    private final String jdbcUrl;

    public H2ScanCacheAdapter(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "Database path must not be null.");
        this.jdbcUrl = "jdbc:h2:file:" + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false";
    }

    @Override
    public void initialize() {
        executeInTransaction("initialize", connection -> {
            if (!tableExists(connection, "schema_version")) {
                createSchema(connection);
                return;
            }
            OptionalInt schemaVersion = readSchemaVersion(connection);
            if (schemaVersion.isEmpty() || schemaVersion.getAsInt() != CURRENT_SCHEMA_VERSION) {
                rebuildSchema(connection);
                return;
            }
            createSchema(connection);
        });
    }

    @Override
    public Optional<CachedScanResult> find(SourceFileSnapshot source) {
        Objects.requireNonNull(source, "Source snapshot must not be null.");
        try (Connection connection = openConnection()) {
            Optional<SourceRow> sourceRow = readSourceRow(connection, source.rootPath().toString(), source.relativePath());
            if (sourceRow.isEmpty() || !sameFingerprint(source, sourceRow.get().snapshot())) {
                return Optional.empty();
            }
            long sourceFileId = sourceRow.get().id();
            return Optional.of(new CachedScanResult(
                    sourceRow.get().snapshot(),
                    readEvents(connection, sourceFileId),
                    readDependencies(connection, sourceFileId),
                    readProfile(connection, sourceFileId)));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read H2 scan cache.", e);
        }
    }

    @Override
    public void store(CachedScanResult result) {
        Objects.requireNonNull(result, "Cached scan result must not be null.");
        executeInTransaction("store", connection -> {
            upsertSourceFile(connection, result.source());
            long sourceFileId = readSourceFileId(
                    connection,
                    result.source().rootPath().toString(),
                    result.source().relativePath());
            deleteCachedChildren(connection, sourceFileId);
            insertScanRun(connection, sourceFileId);
            insertEvents(connection, sourceFileId, result.events());
            insertDependencies(connection, sourceFileId, result.dependencies());
            insertProfile(connection, sourceFileId, result.profile());
        });
    }

    @Override
    public void deleteMissing(String rootPath, Set<String> currentRelativePaths) {
        Objects.requireNonNull(rootPath, "Root path must not be null.");
        Objects.requireNonNull(currentRelativePaths, "Current relative paths must not be null.");
        if (currentRelativePaths.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Current relative paths must not contain null values.");
        }
        executeInTransaction("delete stale source files", connection -> deleteMissingInTransaction(
                connection,
                rootPath,
                currentRelativePaths.stream().sorted().toList()));
    }

    @Override
    public void rebuild() {
        executeInTransaction("rebuild", H2ScanCacheAdapter::rebuildSchema);
    }

    private Connection openConnection() throws SQLException {
        Path parent = databasePath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create H2 scan cache directory " + parent + ".", e);
            }
        }
        loadH2Driver();
        return DriverManager.getConnection(jdbcUrl);
    }

    private static void loadH2Driver() throws SQLException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("H2 JDBC driver is not available on the scan cache classpath.", e);
        }
    }

    private void executeInTransaction(String operation, SqlWork work) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            executeAndCommit(connection, work);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to " + operation + " H2 scan cache.", e);
        }
    }

    private static void executeAndCommit(Connection connection, SqlWork work) throws SQLException {
        try {
            work.execute(connection);
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            rollback(connection, e);
            throw e;
        }
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metadata.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private static OptionalInt readSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version")) {
            if (!resultSet.next()) {
                return OptionalInt.empty();
            }
            int version = resultSet.getInt("version");
            if (resultSet.next()) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(version);
        }
    }

    private static void rebuildSchema(Connection connection) throws SQLException {
        dropSchema(connection);
        createSchema(connection);
    }

    private static void dropSchema(Connection connection) throws SQLException {
        List<String> statements = List.of(
                "DROP TABLE IF EXISTS scan_metric",
                "DROP TABLE IF EXISTS code_dependency",
                "DROP TABLE IF EXISTS scan_event",
                "DROP TABLE IF EXISTS method_declaration",
                "DROP TABLE IF EXISTS type_declaration",
                "DROP TABLE IF EXISTS scan_run",
                "DROP TABLE IF EXISTS source_file",
                "DROP TABLE IF EXISTS schema_version");
        executeBatch(connection, statements);
    }

    private static void createSchema(Connection connection) throws SQLException {
        List<String> statements = List.of(
                """
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS source_file (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    root_path VARCHAR(8192) NOT NULL,
                    relative_path VARCHAR(8192) NOT NULL,
                    source_path VARCHAR(8192) NOT NULL,
                    fingerprint_algorithm VARCHAR(128) NOT NULL,
                    fingerprint_value VARCHAR(256) NOT NULL,
                    file_size BIGINT NOT NULL,
                    last_modified_at VARCHAR(64) NOT NULL,
                    parse_succeeded BOOLEAN NOT NULL,
                    failure_message CLOB,
                    CONSTRAINT uq_source_file_path UNIQUE (root_path, relative_path)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS scan_run (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    source_file_id BIGINT NOT NULL,
                    CONSTRAINT fk_scan_run_source
                        FOREIGN KEY (source_file_id) REFERENCES source_file(id) ON DELETE CASCADE
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS type_declaration (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    source_file_id BIGINT NOT NULL,
                    declaration_order INTEGER NOT NULL,
                    fqcn VARCHAR(1024),
                    CONSTRAINT fk_type_source
                        FOREIGN KEY (source_file_id) REFERENCES source_file(id) ON DELETE CASCADE
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS method_declaration (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    type_declaration_id BIGINT NOT NULL,
                    declaration_order INTEGER NOT NULL,
                    method_name VARCHAR(1024),
                    signature CLOB,
                    CONSTRAINT fk_method_type
                        FOREIGN KEY (type_declaration_id) REFERENCES type_declaration(id) ON DELETE CASCADE
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS scan_event (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    method_declaration_id BIGINT NOT NULL,
                    event_order INTEGER NOT NULL,
                    kind VARCHAR(128),
                    condition_text CLOB,
                    language VARCHAR(128),
                    return_type VARCHAR(1024),
                    event_line INTEGER,
                    CONSTRAINT fk_event_method
                        FOREIGN KEY (method_declaration_id) REFERENCES method_declaration(id) ON DELETE CASCADE
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS code_dependency (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    source_file_id BIGINT NOT NULL,
                    dependency_order INTEGER NOT NULL,
                    kind VARCHAR(128) NOT NULL,
                    source_relative_path VARCHAR(8192) NOT NULL,
                    owner_type VARCHAR(1024) NOT NULL,
                    owner_member VARCHAR(1024) NOT NULL,
                    target CLOB NOT NULL,
                    source_line INTEGER NOT NULL,
                    source_column INTEGER NOT NULL,
                    CONSTRAINT fk_dependency_source
                        FOREIGN KEY (source_file_id) REFERENCES source_file(id) ON DELETE CASCADE
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS scan_metric (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    source_file_id BIGINT NOT NULL,
                    metric_order INTEGER NOT NULL,
                    metric_name VARCHAR(128) NOT NULL,
                    duration_nanos BIGINT,
                    metric_value INTEGER,
                    CONSTRAINT fk_metric_source
                        FOREIGN KEY (source_file_id) REFERENCES source_file(id) ON DELETE CASCADE
                )
                """);
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.addBatch(sql);
            }
            statement.executeBatch();
            statement.executeUpdate("DELETE FROM schema_version");
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (" + CURRENT_SCHEMA_VERSION + ")");
        }
    }

    private static void executeBatch(Connection connection, List<String> statements) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.addBatch(sql);
            }
            statement.executeBatch();
        }
    }

    private static void upsertSourceFile(Connection connection, SourceFileSnapshot source) throws SQLException {
        String sql = """
                MERGE INTO source_file (
                    root_path,
                    relative_path,
                    source_path,
                    fingerprint_algorithm,
                    fingerprint_value,
                    file_size,
                    last_modified_at,
                    parse_succeeded,
                    failure_message
                )
                KEY (root_path, relative_path)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source.rootPath().toString());
            statement.setString(2, source.relativePath());
            statement.setString(3, source.sourcePath().toString());
            statement.setString(4, source.fingerprint().algorithm());
            statement.setString(5, source.fingerprint().value());
            statement.setLong(6, source.size());
            statement.setString(7, source.lastModifiedAt().toString());
            statement.setBoolean(8, source.parseSucceeded());
            setNullableString(statement, 9, source.failureMessage().orElse(null));
            statement.executeUpdate();
        }
    }

    private static long readSourceFileId(Connection connection, String rootPath, String relativePath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                FROM source_file
                WHERE root_path = ? AND relative_path = ?
                """)) {
            statement.setString(1, rootPath);
            statement.setString(2, relativePath);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("id");
                }
                throw new IllegalStateException("Stored source file was not found in the scan cache.");
            }
        }
    }

    private static Optional<SourceRow> readSourceRow(
            Connection connection,
            String rootPath,
            String relativePath
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,
                       root_path,
                       relative_path,
                       source_path,
                       fingerprint_algorithm,
                       fingerprint_value,
                       file_size,
                       last_modified_at,
                       parse_succeeded,
                       failure_message
                FROM source_file
                WHERE root_path = ? AND relative_path = ?
                """)) {
            statement.setString(1, rootPath);
            statement.setString(2, relativePath);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                SourceFileSnapshot snapshot = new SourceFileSnapshot(
                        Path.of(resultSet.getString("root_path")),
                        resultSet.getString("relative_path"),
                        Path.of(resultSet.getString("source_path")),
                        new SourceFileFingerprint(
                                resultSet.getString("fingerprint_algorithm"),
                                resultSet.getString("fingerprint_value")),
                        resultSet.getLong("file_size"),
                        Instant.parse(resultSet.getString("last_modified_at")),
                        resultSet.getBoolean("parse_succeeded"),
                        Optional.ofNullable(resultSet.getString("failure_message")));
                return Optional.of(new SourceRow(resultSet.getLong("id"), snapshot));
            }
        }
    }

    private static void deleteCachedChildren(Connection connection, long sourceFileId) throws SQLException {
        executeUpdate(connection, "DELETE FROM scan_metric WHERE source_file_id = ?", sourceFileId);
        executeUpdate(connection, "DELETE FROM code_dependency WHERE source_file_id = ?", sourceFileId);
        executeUpdate(connection, "DELETE FROM type_declaration WHERE source_file_id = ?", sourceFileId);
        executeUpdate(connection, "DELETE FROM scan_run WHERE source_file_id = ?", sourceFileId);
    }

    private static void executeUpdate(Connection connection, String sql, long sourceFileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sourceFileId);
            statement.executeUpdate();
        }
    }

    private static void insertScanRun(Connection connection, long sourceFileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO scan_run (source_file_id)
                VALUES (?)
                """)) {
            statement.setLong(1, sourceFileId);
            statement.executeUpdate();
        }
    }

    private static void insertEvents(
            Connection connection,
            long sourceFileId,
            List<ScanEvent> events
    ) throws SQLException {
        for (int index = 0; index < events.size(); index++) {
            ScanEvent event = events.get(index);
            SourceLocation location = event.location();
            Long typeId = insertTypeDeclaration(
                    connection,
                    sourceFileId,
                    index,
                    location == null ? null : location.fqcn());
            Long methodId = insertMethodDeclaration(
                    connection,
                    typeId,
                    index,
                    location == null ? null : location.method(),
                    event.signature());
            insertScanEvent(connection, methodId, index, event);
        }
    }

    private static long insertTypeDeclaration(
            Connection connection,
            long sourceFileId,
            int declarationOrder,
            String fqcn
    ) throws SQLException {
        return insertAndReturnId(connection, """
                INSERT INTO type_declaration (source_file_id, declaration_order, fqcn)
                VALUES (?, ?, ?)
                """, statement -> {
            statement.setLong(1, sourceFileId);
            statement.setInt(2, declarationOrder);
            setNullableString(statement, 3, fqcn);
        });
    }

    private static long insertMethodDeclaration(
            Connection connection,
            long typeDeclarationId,
            int declarationOrder,
            String methodName,
            String signature
    ) throws SQLException {
        return insertAndReturnId(connection, """
                INSERT INTO method_declaration (type_declaration_id, declaration_order, method_name, signature)
                VALUES (?, ?, ?, ?)
                """, statement -> {
            statement.setLong(1, typeDeclarationId);
            statement.setInt(2, declarationOrder);
            setNullableString(statement, 3, methodName);
            setNullableString(statement, 4, signature);
        });
    }

    private static void insertScanEvent(
            Connection connection,
            long methodDeclarationId,
            int eventOrder,
            ScanEvent event
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO scan_event (
                    method_declaration_id,
                    event_order,
                    kind,
                    condition_text,
                    language,
                    return_type,
                    event_line
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, methodDeclarationId);
            statement.setInt(2, eventOrder);
            setNullableString(statement, 3, event.kind() == null ? null : event.kind().name());
            setNullableString(statement, 4, event.conditionText());
            setNullableString(statement, 5, event.language());
            setNullableString(statement, 6, event.returnType());
            setNullableInteger(statement, 7, event.location() == null ? null : event.location().line());
            statement.executeUpdate();
        }
    }

    private static List<ScanEvent> readEvents(Connection connection, long sourceFileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT e.event_order,
                       td.fqcn,
                       md.method_name,
                       e.event_line,
                       md.signature,
                       e.kind,
                       e.condition_text,
                       e.language,
                       e.return_type
                FROM scan_event e
                JOIN method_declaration md ON md.id = e.method_declaration_id
                JOIN type_declaration td ON td.id = md.type_declaration_id
                WHERE td.source_file_id = ?
                ORDER BY e.event_order
                """)) {
            statement.setLong(1, sourceFileId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ScanEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    String kind = resultSet.getString("kind");
                    Integer eventLine = nullableInteger(resultSet, "event_line");
                    SourceLocation location = readLocation(
                            resultSet.getString("fqcn"),
                            resultSet.getString("method_name"),
                            eventLine);
                    events.add(new ScanEvent(
                            location,
                            resultSet.getString("signature"),
                            kind == null ? null : RuleTemplate.valueOf(kind),
                            resultSet.getString("condition_text"),
                            resultSet.getString("language"),
                            resultSet.getString("return_type")));
                }
                return List.copyOf(events);
            }
        }
    }

    private static SourceLocation readLocation(String fqcn, String methodName, Integer line) {
        if (fqcn == null && methodName == null && line == null) {
            return null;
        }
        return new SourceLocation(fqcn, methodName, line == null ? 0 : line);
    }

    private static boolean sameFingerprint(SourceFileSnapshot requested, SourceFileSnapshot cached) {
        return requested.rootPath().equals(cached.rootPath())
                && requested.relativePath().equals(cached.relativePath())
                && requested.fingerprint().equals(cached.fingerprint())
                && requested.size() == cached.size()
                && requested.lastModifiedAt().equals(cached.lastModifiedAt());
    }

    private static void insertDependencies(
            Connection connection,
            long sourceFileId,
            List<ScanDependency> dependencies
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO code_dependency (
                    source_file_id,
                    dependency_order,
                    kind,
                    source_relative_path,
                    owner_type,
                    owner_member,
                    target,
                    source_line,
                    source_column
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, sourceFileId);
            for (int index = 0; index < dependencies.size(); index++) {
                ScanDependency dependency = dependencies.get(index);
                statement.setInt(2, index);
                statement.setString(3, dependency.kind().cacheToken());
                statement.setString(4, dependency.sourceRelativePath());
                statement.setString(5, dependency.ownerType());
                statement.setString(6, dependency.ownerMember());
                statement.setString(7, dependency.target());
                statement.setInt(8, dependency.line());
                statement.setInt(9, dependency.column());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<ScanDependency> readDependencies(Connection connection, long sourceFileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT kind,
                       source_relative_path,
                       owner_type,
                       owner_member,
                       target,
                       source_line,
                       source_column
                FROM code_dependency
                WHERE source_file_id = ?
                ORDER BY dependency_order
                """)) {
            statement.setLong(1, sourceFileId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ScanDependency> dependencies = new ArrayList<>();
                while (resultSet.next()) {
                    dependencies.add(new ScanDependency(
                            DependencyKind.fromCacheToken(resultSet.getString("kind")),
                            resultSet.getString("source_relative_path"),
                            resultSet.getString("owner_type"),
                            resultSet.getString("owner_member"),
                            resultSet.getString("target"),
                            resultSet.getInt("source_line"),
                            resultSet.getInt("source_column")));
                }
                return List.copyOf(dependencies);
            }
        }
    }

    private static void insertProfile(
            Connection connection,
            long sourceFileId,
            ScanProfile profile
    ) throws SQLException {
        int order = 0;
        for (CounterMetric metric : CounterMetric.values()) {
            insertMetric(connection, sourceFileId, order, metric.metricName(), null, metric.value(profile));
            order++;
        }
        for (ScanPhase phase : ScanPhase.values()) {
            Duration duration = profile.phaseDurations().get(phase);
            if (duration != null) {
                insertMetric(
                        connection,
                        sourceFileId,
                        order,
                        PHASE_METRIC_PREFIX + phase.name(),
                        duration.toNanos(),
                        null);
                order++;
            }
        }
    }

    private static void insertMetric(
            Connection connection,
            long sourceFileId,
            int metricOrder,
            String metricName,
            Long durationNanos,
            Integer metricValue
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO scan_metric (
                    source_file_id,
                    metric_order,
                    metric_name,
                    duration_nanos,
                    metric_value
                )
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, sourceFileId);
            statement.setInt(2, metricOrder);
            statement.setString(3, metricName);
            setNullableLong(statement, 4, durationNanos);
            setNullableInteger(statement, 5, metricValue);
            statement.executeUpdate();
        }
    }

    private static ScanProfile readProfile(Connection connection, long sourceFileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT metric_name,
                       duration_nanos,
                       metric_value
                FROM scan_metric
                WHERE source_file_id = ?
                ORDER BY metric_order
                """)) {
            statement.setLong(1, sourceFileId);
            try (ResultSet resultSet = statement.executeQuery()) {
                EnumMap<ScanPhase, Duration> phaseDurations = new EnumMap<>(ScanPhase.class);
                EnumMap<CounterMetric, Integer> counters = new EnumMap<>(CounterMetric.class);
                while (resultSet.next()) {
                    readMetric(resultSet, phaseDurations, counters);
                }
                return new ScanProfile(
                        phaseDurations,
                        counter(counters, CounterMetric.TOTAL_FILES),
                        counter(counters, CounterMetric.PARSED_FILES),
                        counter(counters, CounterMetric.CACHE_HIT_FILES),
                        counter(counters, CounterMetric.CACHE_MISS_FILES),
                        counter(counters, CounterMetric.FAILED_FILES),
                        counter(counters, CounterMetric.TOTAL_METHODS),
                        counter(counters, CounterMetric.TOTAL_EVENTS),
                        counter(counters, CounterMetric.TOTAL_DEPENDENCIES));
            }
        }
    }

    private static void readMetric(
            ResultSet resultSet,
            Map<ScanPhase, Duration> phaseDurations,
            Map<CounterMetric, Integer> counters
    ) throws SQLException {
        String metricName = resultSet.getString("metric_name");
        Optional<CounterMetric> counterMetric = CounterMetric.fromMetricName(metricName);
        if (counterMetric.isPresent()) {
            counters.put(counterMetric.get(), resultSet.getInt("metric_value"));
            return;
        }
        if (metricName.startsWith(PHASE_METRIC_PREFIX)) {
            ScanPhase phase = ScanPhase.valueOf(metricName.substring(PHASE_METRIC_PREFIX.length()));
            phaseDurations.put(phase, Duration.ofNanos(resultSet.getLong("duration_nanos")));
        }
    }

    private static int counter(Map<CounterMetric, Integer> counters, CounterMetric metric) {
        return counters.getOrDefault(metric, 0);
    }

    private static void deleteMissingInTransaction(
            Connection connection,
            String rootPath,
            List<String> currentRelativePaths
    ) throws SQLException {
        if (currentRelativePaths.isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM source_file
                    WHERE root_path = ?
                    """)) {
                statement.setString(1, rootPath);
                statement.executeUpdate();
            }
            return;
        }

        List<Long> staleSourceFileIds = readStaleSourceFileIds(
                connection,
                rootPath,
                Set.copyOf(currentRelativePaths));
        deleteSourceFilesById(connection, staleSourceFileIds);
    }

    private static List<Long> readStaleSourceFileIds(
            Connection connection,
            String rootPath,
            Set<String> retainedRelativePaths
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, relative_path
                FROM source_file
                WHERE root_path = ?
                """)) {
            statement.setString(1, rootPath);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Long> staleSourceFileIds = new ArrayList<>();
                while (resultSet.next()) {
                    if (!retainedRelativePaths.contains(resultSet.getString("relative_path"))) {
                        staleSourceFileIds.add(resultSet.getLong("id"));
                    }
                }
                return staleSourceFileIds;
            }
        }
    }

    private static void deleteSourceFilesById(Connection connection, List<Long> sourceFileIds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM source_file
                WHERE id = ?
                """)) {
            for (Long sourceFileId : sourceFileIds) {
                statement.setLong(1, sourceFileId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static long insertAndReturnId(
            Connection connection,
            String sql,
            StatementBinder binder
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(statement);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
                throw new IllegalStateException("H2 did not return a generated scan cache identifier.");
            }
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }
        statement.setInt(index, value);
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    private static Integer nullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private record SourceRow(long id, SourceFileSnapshot snapshot) {
    }

    private enum CounterMetric {
        TOTAL_FILES("totalFiles", ScanProfile::totalFiles),
        PARSED_FILES("parsedFiles", ScanProfile::parsedFiles),
        CACHE_HIT_FILES("cacheHitFiles", ScanProfile::cacheHitFiles),
        CACHE_MISS_FILES("cacheMissFiles", ScanProfile::cacheMissFiles),
        FAILED_FILES("failedFiles", ScanProfile::failedFiles),
        TOTAL_METHODS("totalMethods", ScanProfile::totalMethods),
        TOTAL_EVENTS("totalEvents", ScanProfile::totalEvents),
        TOTAL_DEPENDENCIES("totalDependencies", ScanProfile::totalDependencies);

        private final String metricName;
        private final ToIntFunction<ScanProfile> valueReader;

        CounterMetric(String metricName, ToIntFunction<ScanProfile> valueReader) {
            this.metricName = metricName;
            this.valueReader = valueReader;
        }

        String metricName() {
            return metricName;
        }

        int value(ScanProfile profile) {
            return valueReader.applyAsInt(profile);
        }

        static Optional<CounterMetric> fromMetricName(String metricName) {
            return Arrays.stream(values())
                    .filter(metric -> metric.metricName.equals(metricName))
                    .findFirst();
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
