package de.burger.forensics.domain.strategy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Handles basic boolean compositions like "a && b" or "a || b" and parenthesized forms.
 * Also sanitizes accidental single '&' or '|' into their short-circuit forms '&&'/'||'.
 */
public final class LogicalExprStrategy implements ConditionStrategy {
    private static final Pattern SINGLE_AMP = Pattern.compile("(?<!&)&(?!&)");
    private static final Pattern SINGLE_BAR = Pattern.compile("(?<!\\|)\\|(?!\\|)");

    private final String raw;

    public LogicalExprStrategy(String raw) {
        this.raw = Objects.requireNonNull(raw);
    }

    @Override
    public String toBytemanIf() {
        String s = raw.trim();
        // Normalize accidental bitwise operators to logical short-circuit operators.
        s = SINGLE_AMP.matcher(s).replaceAll("&&");
        s = SINGLE_BAR.matcher(s).replaceAll("||");
        return s;
    }
}
