package de.burger.forensics.domain.model.analysis;

/**
 * Stable checksum metadata for an artifact produced during analysis.
 */
public record ArtifactChecksum(String path,
                               String type,
                               String sha256,
                               long sizeBytes) {

    public ArtifactChecksum {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Artifact path must not be blank.");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Artifact type must not be blank.");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("Artifact checksum must not be blank.");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("Artifact size must not be negative.");
        }
    }
}
