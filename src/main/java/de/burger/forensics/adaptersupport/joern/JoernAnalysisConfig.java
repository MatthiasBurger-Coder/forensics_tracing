package de.burger.forensics.adaptersupport.joern;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * CLI-level configuration for a Joern semantic analysis run.
 */
public record JoernAnalysisConfig(Path joernExecutable,
                                  Path joernParseExecutable,
                                  Path joernSliceExecutable,
                                  Duration timeout,
                                  boolean failOnError) {

    public JoernAnalysisConfig {
        Objects.requireNonNull(joernExecutable, "Joern executable must not be null.");
        Objects.requireNonNull(joernParseExecutable, "Joern parse executable must not be null.");
        Objects.requireNonNull(joernSliceExecutable, "Joern slice executable must not be null.");
        Objects.requireNonNull(timeout, "Timeout must not be null.");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive.");
        }
    }
}
