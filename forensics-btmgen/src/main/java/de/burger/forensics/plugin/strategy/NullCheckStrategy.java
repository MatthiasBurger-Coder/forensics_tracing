package de.burger.forensics.plugin.strategy;

import java.util.Objects;

/**
 * Handles "x == null" and "x != null" patterns.
 */
public final class NullCheckStrategy implements ConditionStrategy {
    private final String raw;

    public NullCheckStrategy(String raw) {
        this.raw = Objects.requireNonNull(raw);
    }

    @Override
    public String toBytemanIf() {
        // Keep the raw expression; a separate sanitizer/translator should have escaped strings, if any.
        return raw;
    }
}
