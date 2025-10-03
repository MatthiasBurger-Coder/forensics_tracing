package de.burger.forensics.domain.model;

/**
 * Enumerates the supported rule types.
 */
public enum RuleTemplate {
    IF_TRUE,
    IF_FALSE,
    SWITCH,
    SWITCH_CASE,
    RETURN,
    THROW,
    METHOD_ENTER,
    METHOD_EXIT,
    THREAD_LIFECYCLE,
    JDBC_EXECUTE
}
