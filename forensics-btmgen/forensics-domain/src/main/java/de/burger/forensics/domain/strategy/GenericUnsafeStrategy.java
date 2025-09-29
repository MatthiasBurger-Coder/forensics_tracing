package de.burger.forensics.domain.strategy;

import java.util.Objects;

/**
 * Fallback strategy that currently returns the raw expression as-is.
 * This keeps domain independent from plugin-specific translators and
 * matches the expectations of existing tests.
 */
public final class GenericUnsafeStrategy implements ConditionStrategy {
    private final String raw;

    public GenericUnsafeStrategy(String raw) {
        this.raw = Objects.requireNonNull(raw);
    }

    @Override
    public String toBytemanIf() {
        return raw;
    }
}
