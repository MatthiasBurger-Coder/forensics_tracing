package de.burger.forensics.plugin.btmgen.common;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Build-tool-neutral result model for Byteman rule generation.
 */
public record BtmGenerationResult(
        Path outputFile,
        Path profileReportFile,
        int generatedRuleCount,
        int scannedFileCount,
        int parsedFileCount,
        int failedFileCount,
        int cacheHitCount,
        int cacheMissCount
) {

    public BtmGenerationResult {
        outputFile = Objects.requireNonNull(outputFile, "outputFile");
        profileReportFile = Objects.requireNonNull(profileReportFile, "profileReportFile");
        requireNonNegative(generatedRuleCount, "generatedRuleCount");
        requireNonNegative(scannedFileCount, "scannedFileCount");
        requireNonNegative(parsedFileCount, "parsedFileCount");
        requireNonNegative(failedFileCount, "failedFileCount");
        requireNonNegative(cacheHitCount, "cacheHitCount");
        requireNonNegative(cacheMissCount, "cacheMissCount");
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
