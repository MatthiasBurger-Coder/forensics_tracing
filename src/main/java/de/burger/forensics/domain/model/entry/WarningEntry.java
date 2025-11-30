package de.burger.forensics.domain.model.entry;

import java.time.Instant;

/**
 * Represents a non-fatal issue encountered during analysis.
 */
public record WarningEntry(
        Instant timestamp,
        String message,
        String source
) {
    public WarningEntry(String message, String source) {
        this(Instant.now(), message, source);
    }
}
