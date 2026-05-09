package de.burger.forensics.adaptersupport.persistence.h2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlTransactionRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void commitsSuccessfulWork() throws SQLException {
        Path databasePath = tempDir.resolve("successful-transaction");
        SqlTransactionRunner runner = runner(databasePath);

        createTable(runner);
        runner.run("insert row", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO audit_log (message) VALUES ('ok')");
            }
        });

        assertThat(rowCount(databasePath)).isEqualTo(1L);
    }

    @Test
    void rollsBackSqlFailuresAndWrapsThemWithOperationContext() throws SQLException {
        Path databasePath = tempDir.resolve("sql-failure");
        SqlTransactionRunner runner = runner(databasePath);

        createTable(runner);

        assertThatThrownBy(() -> runner.run("insert row", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO audit_log (message) VALUES ('before failure')");
            }
            throw new SQLException("boom");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to insert row H2 analysis store.")
                .cause()
                .isInstanceOf(SQLException.class)
                .hasMessage("boom");

        assertThat(rowCount(databasePath)).isZero();
    }

    @Test
    void rollsBackRuntimeFailuresAndPropagatesTheOriginalException() throws SQLException {
        Path databasePath = tempDir.resolve("runtime-failure");
        SqlTransactionRunner runner = runner(databasePath);

        createTable(runner);

        assertThatThrownBy(() -> runner.run("insert row", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO audit_log (message) VALUES ('before failure')");
            }
            throw new IllegalArgumentException("boom");
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom");

        assertThat(rowCount(databasePath)).isZero();
    }

    private static SqlTransactionRunner runner(Path databasePath) {
        return new SqlTransactionRunner(new H2ConnectionFactory(databasePath));
    }

    private static void createTable(SqlTransactionRunner runner) {
        runner.run("create table", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE audit_log (message VARCHAR(255))");
            }
        });
    }

    private static long rowCount(Path databasePath) throws SQLException {
        try (Connection connection = connect(databasePath);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM audit_log")) {
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
