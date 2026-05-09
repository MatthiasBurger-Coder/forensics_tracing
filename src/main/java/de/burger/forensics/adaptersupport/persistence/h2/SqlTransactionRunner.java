package de.burger.forensics.adaptersupport.persistence.h2;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Executes H2 work inside one transaction.
 */
public final class SqlTransactionRunner {

    private final H2ConnectionFactory connectionFactory;

    public SqlTransactionRunner(H2ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void run(String operation, SqlWork work) {
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                work.execute(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to " + operation + " H2 analysis store.", e);
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
