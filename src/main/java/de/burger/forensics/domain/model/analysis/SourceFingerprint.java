package de.burger.forensics.domain.model.analysis;

/**
 * Aggregate content fingerprint of all analyzed Java source files.
 */
public record SourceFingerprint(String value) {

    public SourceFingerprint {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Source fingerprint must not be blank.");
        }
    }
}
