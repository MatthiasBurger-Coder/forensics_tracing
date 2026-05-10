package de.burger.forensics.adaptersupport.persistence.h2;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Executes H2 work inside one transaction.
 */
public final class SqlTransactionRunner {

    private static final String DEFAULT_TARGET_DESCRIPTION = "H2 analysis store";

    private final H2ConnectionFactory connectionFactory;
    private final String targetDescription;

    public SqlTransactionRunner(H2ConnectionFactory connectionFactory) {
        this(connectionFactory, DEFAULT_TARGET_DESCRIPTION);
    }

    public SqlTransactionRunner(H2ConnectionFactory connectionFactory, String targetDescription) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.targetDescription = Objects.requireNonNull(targetDescription, "targetDescription");
    }

    public void run(String operation, SqlWork work) {
        try (Connection connection = connectionFactory.openConnection()) {
            executeInTransaction(connection, work);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to " + operation + " " + targetDescription + ".", e);
        }
    }

    private static void executeInTransaction(Connection connection, SqlWork work) throws SQLException {
        connection.setAutoCommit(false);
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

    @FunctionalInterface
    public interface SqlWork {
        void execute(Connection connection) throws SQLException;
    }
}
