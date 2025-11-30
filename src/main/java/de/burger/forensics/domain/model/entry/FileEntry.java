package de.burger.forensics.domain.model.entry;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Represents a source file discovered during the analysis run.
 */
public record FileEntry(
        Path path,
        long fileSize,
        Instant discoveredAt
) {
    public FileEntry(Path path, long fileSize) {
        this(path, fileSize, Instant.now());
    }
}
