package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.domain.validation.ConditionValidationReport;

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
        int cacheMissCount,
        ConditionValidationReport validationReport
) {

    public BtmGenerationResult {
        Objects.requireNonNull(outputFile, "outputFile");
        Objects.requireNonNull(profileReportFile, "profileReportFile");
        requireNonNegative(generatedRuleCount, "generatedRuleCount");
        requireNonNegative(scannedFileCount, "scannedFileCount");
        requireNonNegative(parsedFileCount, "parsedFileCount");
        requireNonNegative(failedFileCount, "failedFileCount");
        requireNonNegative(cacheHitCount, "cacheHitCount");
        requireNonNegative(cacheMissCount, "cacheMissCount");
        Objects.requireNonNull(validationReport, "validationReport");
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
