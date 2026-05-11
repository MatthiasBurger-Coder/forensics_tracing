package de.burger.forensics.adaptersupport.persistence.h2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class H2ConnectionFactoryTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreH2Driver() throws SQLException {
        DriverManager.registerDriver(new org.h2.Driver());
    }

    @Test
    void opensConnectionWhenNormalizedDatabasePathHasNoParent() throws SQLException {
        Path databasePath = parentlessPath(tempDir.resolve("rootless-analysis-store"));

        try (Connection connection = new H2ConnectionFactory(databasePath).openConnection()) {
            assertThat(connection.isClosed()).isFalse();
        }
    }

    @Test
    void opensConnectionWhenDriverManagerDoesNotAlreadyExposeH2Driver() throws SQLException {
        deregisterH2Drivers();

        try (Connection connection = new H2ConnectionFactory(tempDir.resolve("analysis-store")).openConnection()) {
            assertThat(connection.isClosed()).isFalse();
        }
    }

    private static void deregisterH2Drivers() throws SQLException {
        List<Driver> drivers = new ArrayList<>();
        DriverManager.getDrivers().asIterator().forEachRemaining(drivers::add);
        for (Driver driver : drivers) {
            if (driver.getClass().getName().equals("org.h2.Driver")) {
                DriverManager.deregisterDriver(driver);
            }
        }
    }

    private static Path parentlessPath(Path target) {
        return (Path) Proxy.newProxyInstance(
                H2ConnectionFactoryTest.class.getClassLoader(),
                new Class<?>[]{Path.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toAbsolutePath", "normalize" -> proxy;
                    case "getParent" -> null;
                    case "toString" -> target.toAbsolutePath().normalize().toString();
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
