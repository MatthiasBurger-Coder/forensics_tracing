package de.burger.forensics.domain.model.cache;

/**
 * Stable content identity for a source file.
 */
public record SourceFileFingerprint(String algorithm, String value) {
    public SourceFileFingerprint {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Fingerprint algorithm must not be blank.");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Fingerprint value must not be blank.");
        }
    }
}
