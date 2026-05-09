package de.burger.forensics.application.service;

import de.burger.forensics.domain.model.analysis.SourceFileSnapshot;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;

import java.util.List;
import java.util.Objects;

/**
 * Source file snapshots and their aggregate fingerprint.
 */
public record SourceFingerprintResult(SourceFingerprint sourceFingerprint,
                                      List<SourceFileSnapshot> sourceFiles) {

    public SourceFingerprintResult {
        Objects.requireNonNull(sourceFingerprint, "Source fingerprint must not be null.");
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "Source files must not be null."));
    }
}
