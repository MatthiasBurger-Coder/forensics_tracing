package de.burger.forensics.domain.strategy;

import java.util.Objects;

/**
 * Handles basic boolean compositions like "a && b" or "a || b" and parenthesized forms.
 * We keep the expression intact assuming it has been sanitized elsewhere.
 */
public final class LogicalExprStrategy implements ConditionStrategy {
    private final String raw;

    public LogicalExprStrategy(String raw) {
        this.raw = Objects.requireNonNull(raw);
    }

    @Override
    public String toBytemanIf() {
        return raw;
    }
}
