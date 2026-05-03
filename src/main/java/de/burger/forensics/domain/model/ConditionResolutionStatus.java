package de.burger.forensics.domain.model;

/**
 * Describes how a source-level condition reference was resolved or why it stayed unresolved.
 */
public enum ConditionResolutionStatus {
    RESOLVED_BY_SYMBOL_SOLVER,
    RESOLVED_BY_EXPLICIT_IMPORT,
    RESOLVED_BY_SAME_PACKAGE,
    RESOLVED_BY_NESTED_TYPE,
    UNRESOLVED,
    AMBIGUOUS,
    UNSUPPORTED
}
