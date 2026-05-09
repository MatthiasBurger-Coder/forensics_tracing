package de.burger.forensics.domain.model.analysis;

import java.util.Arrays;

/**
 * Controls whether analysis store files are retained after task execution.
 */
public enum AnalysisStoreCleanupPolicy {
    DELETE_ON_SUCCESS(true, false),
    KEEP_ON_SUCCESS(false, false),
    KEEP_ON_FAILURE(true, false),
    KEEP_ALWAYS(false, false);

    public static final AnalysisStoreCleanupPolicy DEFAULT = KEEP_ON_SUCCESS;

    private final boolean deleteAfterSuccess;
    private final boolean deleteAfterFailure;

    AnalysisStoreCleanupPolicy(boolean deleteAfterSuccess, boolean deleteAfterFailure) {
        this.deleteAfterSuccess = deleteAfterSuccess;
        this.deleteAfterFailure = deleteAfterFailure;
    }

    public static AnalysisStoreCleanupPolicy from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return Arrays.stream(values())
                .filter(candidate -> candidate.name().equals(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported analysis store cleanup policy: " + value));
    }

    public boolean shouldDeleteAfterSuccess() {
        return deleteAfterSuccess;
    }

    public boolean shouldDeleteAfterFailure() {
        return deleteAfterFailure;
    }
}
