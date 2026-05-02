package de.burger.forensics.domain.model.cache;

/**
 * Coarse phases measured while resolving parser scan cache entries.
 */
public enum ScanPhase {
    SOURCE_FILE_DISCOVERY,
    FINGERPRINT_CALCULATION,
    TYPE_SOLVER_SETUP,
    JAVA_PARSER_PARSE,
    PACKAGE_EXTRACTION,
    METHOD_DISCOVERY,
    CONTEXT_CREATION,
    DEPENDENCY_EXTRACTION,
    EVENT_EXTRACTION,
    CONDITION_RENDERING,
    SYMBOL_RESOLUTION,
    CACHE_READ,
    CACHE_WRITE,
    RULE_RENDERING,
    BTM_FILE_WRITING
}
