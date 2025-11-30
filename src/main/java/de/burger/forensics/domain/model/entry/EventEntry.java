package de.burger.forensics.domain.model.entry;

import de.burger.forensics.domain.model.ScanEvent;

/**
 * Wraps a ScanEvent with additional metadata for export or debugging.
 */
public record EventEntry(
        ScanEvent event,
        String source,
        long index
) {}
