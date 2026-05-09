package de.burger.forensics.domain.model.analysis;

/**
 * Lifecycle state of a persisted static analysis run.
 */
public enum AnalysisRunStatus {
    CREATED,
    SCANNING,
    BTM_GENERATED,
    COMPLETED,
    FAILED
}
