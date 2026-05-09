package de.burger.forensics.adapters.persistence.h2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class H2ScanCacheAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeCreatesSchemaAndRebuildsIncompatibleVersion() throws SQLException {
        Path databasePath = tempDir.resolve("scan-cache");
        H2ScanCacheAdapter adapter = new H2ScanCacheAdapter(databasePath);
        CachedScanResult result = cachedResult(tempDir.resolve("root"), "sample/Sample.java", "hash-a");

        adapter.initialize();
        adapter.store(result);

        assertThat(tableNames(databasePath))
                .contains("schema_version", "cache_scan_run", "cache_source_file", "cache_type_declaration",
                        "cache_method_declaration", "cache_scan_event", "cache_code_dependency", "cache_scan_metric",
                        "analysis_run", "source_file", "scan_method", "scan_event", "btm_rule", "artifact_checksum",
                        "joern_import_run", "joern_node", "joern_edge", "joern_method", "joern_call_relation",
                        "joern_control_flow_relation", "joern_data_flow_path", "joern_data_flow_step",
                        "semantic_anchor");
        assertThat(schemaVersion(databasePath)).isEqualTo(4);
        assertThat(adapter.find(result.source())).contains(result);

        overwriteSchemaVersion(databasePath, 99);

        adapter.initialize();

        assertThat(schemaVersion(databasePath)).isEqualTo(4);
        assertThat(adapter.find(result.source())).isEmpty();
        assertThat(tableNames(databasePath))
                .contains("schema_version", "cache_scan_run", "cache_source_file", "cache_type_declaration",
                        "cache_method_declaration", "cache_scan_event", "cache_code_dependency", "cache_scan_metric",
                        "analysis_run", "source_file", "scan_method", "scan_event", "btm_rule", "artifact_checksum",
                        "joern_import_run", "joern_node", "joern_edge", "joern_method", "joern_call_relation",
                        "joern_control_flow_relation", "joern_data_flow_path", "joern_data_flow_step",
                        "semantic_anchor");
    }

    @Test
    void storeAndLoadRestoresSnapshotEventsDependenciesAndProfile() {
        Path rootPath = tempDir.resolve("root");
        SourceFileSnapshot source = new SourceFileSnapshot(
                rootPath,
                "sample/Sample.java",
                rootPath.resolve("sample/Sample.java"),
                new SourceFileFingerprint("SHA-256", "source-hash"),
                256L,
                Instant.parse("2026-05-02T10:15:30Z"),
                false,
                Optional.of("Previous parser failure"));
        CachedScanResult result = new CachedScanResult(
                source,
                List.of(
                        new ScanEvent(
                                new SourceLocation("sample.Sample", "run", 42),
                                "void run()",
                                RuleTemplate.METHOD_ENTER,
                                "",
                                "java",
                                "void"),
                        new ScanEvent(
                                new SourceLocation("sample.Sample", "find", 47),
                                "String find(int)",
                                RuleTemplate.RETURN,
                                "result",
                                "java",
                                null)),
                List.of(
                        new ScanDependency(
                                DependencyKind.METHOD_CALL,
                                "sample/Sample.java",
                                "sample.Sample",
                                "run",
                                "sample.Dependency.call",
                                42,
                                17),
                        new ScanDependency(
                                DependencyKind.RETURN_TYPE,
                                "sample/Sample.java",
                                "sample.Sample",
                                "find",
                                "java.lang.String",
                                47,
                                12)),
                new ScanProfile(
                        Map.of(
                                ScanPhase.JAVA_PARSER_PARSE, Duration.ofMillis(12),
                                ScanPhase.CACHE_WRITE, Duration.ofNanos(345)),
                        1,
                        1,
                        0,
                        1,
                        0,
                        2,
                        2,
                        2));
        H2ScanCacheAdapter adapter = initializedAdapter(tempDir.resolve("roundtrip-cache"));

        adapter.store(result);

        assertThat(adapter.find(source)).contains(result);
        SourceFileSnapshot changedSource = new SourceFileSnapshot(
                source.rootPath(),
                source.relativePath(),
                source.sourcePath(),
                new SourceFileFingerprint("SHA-256", "changed-hash"),
                source.size(),
                source.lastModifiedAt(),
                source.parseSucceeded(),
                source.failureMessage());
        assertThat(adapter.find(changedSource)).isEmpty();
    }

    @Test
    void deleteMissingRemovesStaleFileDataWithCascades() throws SQLException {
        Path databasePath = tempDir.resolve("stale-cache");
        Path rootPath = tempDir.resolve("root");
        Path otherRootPath = tempDir.resolve("other-root");
        H2ScanCacheAdapter adapter = initializedAdapter(databasePath);
        CachedScanResult current = cachedResult(rootPath, "sample/Current.java", "hash-current");
        CachedScanResult stale = cachedResult(rootPath, "sample/Stale.java", "hash-stale");
        CachedScanResult otherRoot = cachedResult(otherRootPath, "sample/Stale.java", "hash-other-root");
        adapter.store(current);
        adapter.store(stale);
        adapter.store(otherRoot);

        adapter.deleteMissing(rootPath.toString(), Set.of(current.source().relativePath()));

        assertThat(adapter.find(current.source())).contains(current);
        assertThat(adapter.find(stale.source())).isEmpty();
        assertThat(adapter.find(otherRoot.source())).contains(otherRoot);
        assertThat(rowCount(databasePath, "cache_source_file")).isEqualTo(2);
        assertThat(rowCount(databasePath, "cache_scan_event")).isEqualTo(2);
        assertThat(rowCount(databasePath, "cache_code_dependency")).isEqualTo(2);
    }

    @Test
    void initializeRebuildsEmptyOrAmbiguousSchemaVersion() throws SQLException {
        Path emptyVersionDatabase = tempDir.resolve("empty-version-cache");
        try (Connection connection = connect(emptyVersionDatabase);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)");
        }

        H2ScanCacheAdapter emptyVersionAdapter = new H2ScanCacheAdapter(emptyVersionDatabase);
        emptyVersionAdapter.initialize();

        assertThat(schemaVersion(emptyVersionDatabase)).isEqualTo(4);
        assertThat(tableNames(emptyVersionDatabase)).contains("cache_source_file", "cache_scan_event", "cache_code_dependency", "analysis_run", "source_file", "scan_event");

        Path duplicateVersionDatabase = tempDir.resolve("duplicate-version-cache");
        H2ScanCacheAdapter duplicateVersionAdapter = initializedAdapter(duplicateVersionDatabase);
        CachedScanResult result = cachedResult(tempDir.resolve("root"), "sample/Sample.java", "hash-a");
        duplicateVersionAdapter.store(result);
        try (Connection connection = connect(duplicateVersionDatabase);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (1)");
        }

        duplicateVersionAdapter.initialize();

        assertThat(schemaVersion(duplicateVersionDatabase)).isEqualTo(4);
        assertThat(duplicateVersionAdapter.find(result.source())).isEmpty();
    }

    @Test
    void storeAndLoadSupportsNullEventFieldsAndEmptyMetrics() {
        Path rootPath = tempDir.resolve("root");
        SourceFileSnapshot source = new SourceFileSnapshot(
                rootPath,
                "sample/Empty.java",
                rootPath.resolve("sample/Empty.java"),
                new SourceFileFingerprint("SHA-256", "hash-empty"),
                0L,
                Instant.parse("2026-05-02T10:15:30Z"),
                true,
                Optional.empty());
        CachedScanResult result = new CachedScanResult(
                source,
                List.of(new ScanEvent(null, null, null, null, null, null)),
                List.of(),
                ScanProfile.empty());
        H2ScanCacheAdapter adapter = initializedAdapter(tempDir.resolve("nullable-event-cache"));

        adapter.store(result);

        CachedScanResult loaded = adapter.find(source).orElseThrow();
        assertThat(loaded.events()).containsExactly(new ScanEvent(null, null, null, null, null, null));
        assertThat(loaded.dependencies()).isEmpty();
        assertThat(loaded.profile()).isEqualTo(ScanProfile.empty());
    }

    @Test
    void storeAndLoadSupportsDependencyTargetsLongerThanLegacyVarcharLimit() {
        Path rootPath = tempDir.resolve("root");
        SourceFileSnapshot source = new SourceFileSnapshot(
                rootPath,
                "sample/LargeTarget.java",
                rootPath.resolve("sample/LargeTarget.java"),
                new SourceFileFingerprint("SHA-256", "hash-large-target"),
                512L,
                Instant.parse("2026-05-02T10:15:30Z"),
                true,
                Optional.empty());
        String longTarget = "factory.create(" + "argument,".repeat(260) + "result)";
        CachedScanResult result = new CachedScanResult(
                source,
                List.of(),
                List.of(new ScanDependency(
                        DependencyKind.METHOD_CALL,
                        "sample/LargeTarget.java",
                        "sample.LargeTarget",
                        "run",
                        longTarget,
                        23,
                        17)),
                ScanProfile.empty());
        H2ScanCacheAdapter adapter = initializedAdapter(tempDir.resolve("large-target-cache"));

        adapter.store(result);

        assertThat(longTarget).hasSizeGreaterThan(2048);
        assertThat(adapter.find(source)).contains(result);
    }

    @Test
    void findUsesFingerprintAndFileStateButKeepsCachedParseState() {
        Path rootPath = tempDir.resolve("root");
        SourceFileSnapshot failedSource = new SourceFileSnapshot(
                rootPath,
                "sample/Broken.java",
                rootPath.resolve("sample/Broken.java"),
                new SourceFileFingerprint("SHA-256", "broken-hash"),
                13L,
                Instant.parse("2026-05-02T10:15:30Z"),
                false,
                Optional.of("Parse failed"));
        CachedScanResult result = new CachedScanResult(failedSource, List.of(), List.of(), ScanProfile.empty());
        H2ScanCacheAdapter adapter = initializedAdapter(tempDir.resolve("failed-cache"));
        adapter.store(result);
        SourceFileSnapshot currentFingerprint = new SourceFileSnapshot(
                failedSource.rootPath(),
                failedSource.relativePath(),
                failedSource.sourcePath(),
                failedSource.fingerprint(),
                failedSource.size(),
                failedSource.lastModifiedAt(),
                true,
                Optional.empty());

        assertThat(adapter.find(currentFingerprint)).contains(result);
        assertThat(adapter.find(new SourceFileSnapshot(
                failedSource.rootPath(),
                failedSource.relativePath(),
                failedSource.sourcePath(),
                failedSource.fingerprint(),
                failedSource.size() + 1,
                failedSource.lastModifiedAt(),
                true,
                Optional.empty()))).isEmpty();
        assertThat(adapter.find(new SourceFileSnapshot(
                failedSource.rootPath(),
                "sample/Missing.java",
                failedSource.sourcePath(),
                failedSource.fingerprint(),
                failedSource.size(),
                failedSource.lastModifiedAt(),
                true,
                Optional.empty()))).isEmpty();
    }

    @Test
    void deleteMissingRejectsNullPathsAndRemovesAllFilesForEmptyCurrentSet() {
        Path databasePath = tempDir.resolve("empty-delete-cache");
        Path rootPath = tempDir.resolve("root");
        H2ScanCacheAdapter adapter = initializedAdapter(databasePath);
        CachedScanResult stale = cachedResult(rootPath, "sample/Stale.java", "hash-stale");
        CachedScanResult otherRoot = cachedResult(tempDir.resolve("other-root"), "sample/Other.java", "hash-other");
        adapter.store(stale);
        adapter.store(otherRoot);

        Set<String> invalidPaths = new LinkedHashSet<>();
        invalidPaths.add(null);
        String rootPathValue = rootPath.toString();
        assertThatThrownBy(() -> adapter.deleteMissing(rootPathValue, invalidPaths))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain null");

        adapter.deleteMissing(rootPathValue, Set.of());

        assertThat(adapter.find(stale.source())).isEmpty();
        assertThat(adapter.find(otherRoot.source())).contains(otherRoot);
    }

    private H2ScanCacheAdapter initializedAdapter(Path databasePath) {
        H2ScanCacheAdapter adapter = new H2ScanCacheAdapter(databasePath);
        adapter.initialize();
        return adapter;
    }

    private CachedScanResult cachedResult(Path rootPath, String relativePath, String hash) {
        String typeName = relativePath
                .replace('/', '.')
                .replace(".java", "");
        SourceFileSnapshot source = new SourceFileSnapshot(
                rootPath,
                relativePath,
                rootPath.resolve(relativePath),
                new SourceFileFingerprint("SHA-256", hash),
                128L,
                Instant.parse("2026-05-02T10:15:30Z"),
                true,
                Optional.empty());
        return new CachedScanResult(
                source,
                List.of(new ScanEvent(
                        new SourceLocation(typeName, "run", 12),
                        "void run()",
                        RuleTemplate.METHOD_ENTER,
                        "",
                        "java",
                        "void")),
                List.of(new ScanDependency(
                        DependencyKind.METHOD_CALL,
                        relativePath,
                        typeName,
                        "run",
                        "sample.Target.call",
                        12,
                        8)),
                new ScanProfile(
                        Map.of(ScanPhase.JAVA_PARSER_PARSE, Duration.ofMillis(3)),
                        1,
                        1,
                        0,
                        1,
                        0,
                        1,
                        1,
                        1));
    }

    private static Set<String> tableNames(Path databasePath) throws SQLException {
        try (Connection connection = connect(databasePath);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT TABLE_NAME
                     FROM INFORMATION_SCHEMA.TABLES
                     WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'
                     """)) {
            Set<String> names = new LinkedHashSet<>();
            while (resultSet.next()) {
                names.add(resultSet.getString("TABLE_NAME"));
            }
            return names;
        }
    }

    private static int schemaVersion(Path databasePath) throws SQLException {
        try (Connection connection = connect(databasePath);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt("version");
        }
    }

    private static void overwriteSchemaVersion(Path databasePath, int version) throws SQLException {
        try (Connection connection = connect(databasePath);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schema_version");
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (" + version + ")");
        }
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
        String jdbcUrl = "jdbc:h2:file:" + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false";
        return DriverManager.getConnection(jdbcUrl);
    }
}
