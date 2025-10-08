package de.burger.forensics.domain.model;

/**
 * Domain event emitted by the scanning port.
 */
public record ScanEvent(SourceLocation location,
                        String signature,
                        RuleTemplate kind,
                        String conditionText,
                        String language,
                        String returnType) {
}
