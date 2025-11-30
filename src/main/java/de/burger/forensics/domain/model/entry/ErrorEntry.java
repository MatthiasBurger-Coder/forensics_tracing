package de.burger.forensics.domain.model.entry;

import java.time.Instant;

/**
 * Represents a fatal or critical problem that may interrupt the analysis.
 */
public record ErrorEntry(
        Instant timestamp,
        String message,
        String source,
        Throwable cause
) {
    public ErrorEntry(String message, String source) {
        this(Instant.now(), message, source, null);
    }

    public ErrorEntry(String message, String source, Throwable cause) {
        this(Instant.now(), message, source, cause);
    }
}
