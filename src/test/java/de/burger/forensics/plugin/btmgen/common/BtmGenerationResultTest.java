package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.domain.validation.ConditionValidationReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BtmGenerationResultTest {

    private static final Path OUTPUT_FILE = Path.of("build", "forensics", "rules.btm");
    private static final Path PROFILE_REPORT_FILE = Path.of("build", "forensics", "scan-profile.json");
    private static final ConditionValidationReport EMPTY_VALIDATION_REPORT = ConditionValidationReport.empty();

    @Test
    void resultKeepsGeneratedCounts() {
        BtmGenerationResult result = new BtmGenerationResult(
                OUTPUT_FILE,
                PROFILE_REPORT_FILE,
                12,
                7,
                6,
                1,
                4,
                3,
                EMPTY_VALIDATION_REPORT
        );

        assertEquals(OUTPUT_FILE, result.outputFile());
        assertEquals(PROFILE_REPORT_FILE, result.profileReportFile());
        assertEquals(12, result.generatedRuleCount());
        assertEquals(7, result.scannedFileCount());
        assertEquals(6, result.parsedFileCount());
        assertEquals(1, result.failedFileCount());
        assertEquals(4, result.cacheHitCount());
        assertEquals(3, result.cacheMissCount());
        assertFalse(result.validationReport().hasIssues());
    }

    @Test
    void resultRejectsMissingPaths() {
        assertThrows(NullPointerException.class,
                () -> resultWithPathsAndValidation(null, PROFILE_REPORT_FILE, EMPTY_VALIDATION_REPORT));
        assertThrows(NullPointerException.class,
                () -> resultWithPathsAndValidation(OUTPUT_FILE, null, EMPTY_VALIDATION_REPORT));
        assertThrows(NullPointerException.class,
                () -> resultWithPathsAndValidation(OUTPUT_FILE, PROFILE_REPORT_FILE, null));
    }

    @Test
    void resultRejectsNegativeCounts() {
        Map<String, int[]> negativeCounters = Map.of(
                "generatedRuleCount", new int[]{-1, 0, 0, 0, 0, 0},
                "scannedFileCount", new int[]{0, -1, 0, 0, 0, 0},
                "parsedFileCount", new int[]{0, 0, -1, 0, 0, 0},
                "failedFileCount", new int[]{0, 0, 0, -1, 0, 0},
                "cacheHitCount", new int[]{0, 0, 0, 0, -1, 0},
                "cacheMissCount", new int[]{0, 0, 0, 0, 0, -1}
        );

        negativeCounters.forEach((fieldName, counters) -> {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> resultWithCounters(counters)
            );

            assertEquals(fieldName + " must not be negative", exception.getMessage());
        });
    }

    private static BtmGenerationResult resultWithPathsAndValidation(
            Path outputFile,
            Path profileReportFile,
            ConditionValidationReport validationReport
    ) {
        return new BtmGenerationResult(
                outputFile,
                profileReportFile,
                0,
                0,
                0,
                0,
                0,
                0,
                validationReport
        );
    }

    private static BtmGenerationResult resultWithCounters(int[] counters) {
        return new BtmGenerationResult(
                OUTPUT_FILE,
                PROFILE_REPORT_FILE,
                counters[0],
                counters[1],
                counters[2],
                counters[3],
                counters[4],
                counters[5],
                EMPTY_VALIDATION_REPORT
        );
    }
}
