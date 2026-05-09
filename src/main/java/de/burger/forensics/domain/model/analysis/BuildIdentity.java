package de.burger.forensics.domain.model.analysis;

import java.time.Instant;
import java.util.Objects;

/**
 * Shared identity written to BTM rules, manifest files, and the analysis store.
 */
public record BuildIdentity(String projectKey,
                            AnalysisRunId analysisRunId,
                            BuildId buildId,
                            SourceFingerprint sourceFingerprint,
                            String classpathFingerprint,
                            String btmRulesFingerprint,
                            String artifactFingerprint,
                            String pluginVersion,
                            AnalysisSchemaVersion schemaVersion,
                            Instant createdAt) {

    public static final String NOT_COMPUTED = "NOT_COMPUTED";
    public static final String UNKNOWN = "UNKNOWN";

    public BuildIdentity {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("Project key must not be blank.");
        }
        Objects.requireNonNull(analysisRunId, "Analysis run id must not be null.");
        Objects.requireNonNull(buildId, "Build id must not be null.");
        Objects.requireNonNull(sourceFingerprint, "Source fingerprint must not be null.");
        classpathFingerprint = defaultIfBlank(classpathFingerprint, NOT_COMPUTED);
        btmRulesFingerprint = defaultIfBlank(btmRulesFingerprint, NOT_COMPUTED);
        artifactFingerprint = defaultIfBlank(artifactFingerprint, NOT_COMPUTED);
        pluginVersion = defaultIfBlank(pluginVersion, UNKNOWN);
        Objects.requireNonNull(schemaVersion, "Schema version must not be null.");
        Objects.requireNonNull(createdAt, "Created timestamp must not be null.");
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
