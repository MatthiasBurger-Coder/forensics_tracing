package de.burger.forensics.domain.model.analysis;

/**
 * Stable build identity value shared across generated artifacts.
 */
public record BuildId(String value) {

    public BuildId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Build id must not be blank.");
        }
    }
}
