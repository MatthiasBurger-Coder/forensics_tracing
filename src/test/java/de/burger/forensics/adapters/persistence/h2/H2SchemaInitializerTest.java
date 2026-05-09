package de.burger.forensics.adapters.persistence.h2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class H2SchemaInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializesSharedCacheAndAnalysisSchema() throws SQLException {
        Path database = tempDir.resolve("analysis-store");

        new H2SchemaInitializer(database).initialize();

        assertThat(schemaVersion(database)).isEqualTo(4);
        assertThat(tableNames(database))
                .contains("cache_source_file", "cache_scan_event", "analysis_run", "source_file",
                        "scan_method", "scan_event", "btm_rule", "artifact_checksum",
                        "joern_import_run", "joern_node", "joern_edge", "semantic_anchor");
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

    private static Connection connect(Path databasePath) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(databasePath));
    }

    private static String jdbcUrl(Path databasePath) {
        return "jdbc:h2:file:" + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false";
    }
}
