package de.burger.forensics.adaptersupport.persistence.h2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Opens H2 connections for file-backed forensics databases.
 */
public final class H2ConnectionFactory {

    private final Path databasePath;
    private final String jdbcUrl;

    public H2ConnectionFactory(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "Database path must not be null.");
        this.jdbcUrl = "jdbc:h2:file:" + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false";
    }

    public Connection openConnection() throws SQLException {
        Path parent = databasePath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create H2 database directory " + parent + ".", e);
            }
        }
        return DriverManager.getConnection(jdbcUrl);
    }
}
