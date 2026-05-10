package de.burger.forensics.plugin.btmgen.common;

/**
 * Stable analysis payload kinds aligned with the forensic analytics ingestion contract.
 */
public enum EnginePayloadKind {
    SOURCE_FACTS,
    SEMANTIC_ARTIFACTS,
    RULE_ARTIFACTS,
    RUNTIME_TRACE,
    DIAGNOSTIC_REPORT
}
