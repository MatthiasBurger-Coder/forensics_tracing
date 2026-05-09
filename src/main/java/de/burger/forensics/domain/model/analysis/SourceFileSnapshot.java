package de.burger.forensics.domain.model.analysis;

/**
 * Persistable source file metadata for a static analysis run.
 */
public record SourceFileSnapshot(String relativePath,
                                 String absolutePath,
                                 String sha256,
                                 long fileSize,
                                 long lastModifiedEpochMillis) {

    public SourceFileSnapshot {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative source path must not be blank.");
        }
        if (absolutePath == null || absolutePath.isBlank()) {
            throw new IllegalArgumentException("Absolute source path must not be blank.");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("Source checksum must not be blank.");
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("Source file size must not be negative.");
        }
        if (lastModifiedEpochMillis < 0) {
            throw new IllegalArgumentException("Last modified timestamp must not be negative.");
        }
    }
}
