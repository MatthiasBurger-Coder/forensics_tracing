package de.burger.forensics.adapters.persistence.h2;

import de.burger.forensics.domain.port.out.AnalysisSchemaInitializerPort;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Initializes the shared H2 schema used by scan caching and analysis storage.
 */
public final class H2SchemaInitializer implements AnalysisSchemaInitializerPort {

    private final Path databasePath;

    public H2SchemaInitializer(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "Database path must not be null.");
    }

    @Override
    public void initialize() {
        new H2ScanCacheAdapter(databasePath).initialize();
    }
}
