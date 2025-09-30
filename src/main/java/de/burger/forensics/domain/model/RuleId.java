package de.burger.forensics.domain.model;

/**
 * Strongly typed wrapper around the textual rule identifier.
 */
public record RuleId(String value) {
    public RuleId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RuleId must not be blank");
        }
    }
}
