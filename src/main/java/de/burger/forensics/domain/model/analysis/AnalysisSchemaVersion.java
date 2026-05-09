package de.burger.forensics.domain.model.analysis;

/**
 * Version of the persistent analysis store schema.
 */
public record AnalysisSchemaVersion(String value) {

    public static final AnalysisSchemaVersion CURRENT = new AnalysisSchemaVersion("1");

    public AnalysisSchemaVersion {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Analysis schema version must not be blank.");
        }
    }
}
