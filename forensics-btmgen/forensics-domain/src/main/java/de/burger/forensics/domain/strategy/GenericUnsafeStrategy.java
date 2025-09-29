package de.burger.forensics.domain.strategy;

import de.burger.forensics.plugin.translate.UnsafeExprTranslator;

import java.util.Objects;

/**
 * Fallback strategy that runs the expression through UnsafeExprTranslator
 * to get a best-effort Byteman-friendly condition.
 */
public final class GenericUnsafeStrategy implements ConditionStrategy {
    private final String raw;

    public GenericUnsafeStrategy(String raw) {
        this.raw = Objects.requireNonNull(raw);
    }

    @Override
    public String toBytemanIf() {
        return UnsafeExprTranslator.translate(raw);
    }
}
