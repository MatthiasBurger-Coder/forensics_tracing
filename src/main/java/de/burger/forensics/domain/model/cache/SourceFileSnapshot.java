package de.burger.forensics.domain.model.cache;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache-relevant source file state.
 */
public record SourceFileSnapshot(Path rootPath,
                                 String relativePath,
                                 Path sourcePath,
                                 SourceFileFingerprint fingerprint,
                                 long size,
                                 Instant lastModifiedAt,
                                 boolean parseSucceeded,
                                 Optional<String> failureMessage) {
    public SourceFileSnapshot {
        Objects.requireNonNull(rootPath, "Root path must not be null.");
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path must not be blank.");
        }
        Objects.requireNonNull(sourcePath, "Source path must not be null.");
        Objects.requireNonNull(fingerprint, "Source fingerprint must not be null.");
        Objects.requireNonNull(lastModifiedAt, "Last modified timestamp must not be null.");
        Objects.requireNonNull(failureMessage, "Failure message must not be null.");
        if (size < 0) {
            throw new IllegalArgumentException("Source file size must not be negative.");
        }
    }
}
