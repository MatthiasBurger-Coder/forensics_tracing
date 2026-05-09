package de.burger.forensics.domain.model.analysis;

import java.util.Arrays;

/**
 * Controls whether analysis store files are retained after task execution.
 */
public enum AnalysisStoreCleanupPolicy {
    DELETE_ON_SUCCESS,
    KEEP_ON_SUCCESS,
    KEEP_ON_FAILURE,
    KEEP_ALWAYS;

    public static final AnalysisStoreCleanupPolicy DEFAULT = KEEP_ON_SUCCESS;

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
        return this == DELETE_ON_SUCCESS || this == KEEP_ON_FAILURE;
    }

    public boolean shouldDeleteAfterFailure() {
        return false;
    }
}
