package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.cache.SourceFileSnapshot;
import java.nio.file.Path;

/**
 * Port for creating cache-relevant source file snapshots.
 */
public interface SourceFingerprintPort {
    SourceFileSnapshot snapshot(Path rootPath, Path sourceFile);
}
