package de.burger.forensics.plugin.strategy;

import java.util.Objects;

/**
 * Handles "x instanceof Type" patterns (including negations).
 */
public final class InstanceOfStrategy implements ConditionStrategy {
    private final String raw;

    public InstanceOfStrategy(String raw) {
        this.raw = Objects.requireNonNull(raw);
    }

    @Override
    public String toBytemanIf() {
        return raw;
    }
}
