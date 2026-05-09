package de.burger.forensics.domain.model.analysis;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier for one static forensics analysis run.
 */
public record AnalysisRunId(String value) {

    public AnalysisRunId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Analysis run id must not be blank.");
        }
    }

    public static AnalysisRunId random() {
        return new AnalysisRunId(UUID.randomUUID().toString());
    }

    public static AnalysisRunId deterministic(String seed) {
        Objects.requireNonNull(seed, "Seed must not be null.");
        return new AnalysisRunId(UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    }
}
