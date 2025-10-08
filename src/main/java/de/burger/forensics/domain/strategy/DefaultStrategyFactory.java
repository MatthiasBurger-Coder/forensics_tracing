package de.burger.forensics.domain.strategy;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristics-based factory. Keeps detection simple and fast.
 * Falls back to GenericUnsafeStrategy for unknown/complex expressions.
 */
public final class DefaultStrategyFactory implements StrategyFactory {

    private static final Pattern NULL_CHECK =
            Pattern.compile("==\\s*null|!=\\s*null", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTANCE_OF =
            Pattern.compile("\\binstanceof\\b");
    private static final Pattern LOGICAL =
            Pattern.compile("&&|\\|\\|");

    @Override
    public ConditionStrategy from(String rawExpression) {
        if (rawExpression == null || rawExpression.isBlank()) {
            return new GenericUnsafeStrategy("true");
        }
        final String sanitized = InstanceOfPatternSanitizer.sanitize(rawExpression.trim());
        final String lower = sanitized.toLowerCase(Locale.ROOT);

        if (LOGICAL.matcher(sanitized).find()) {
            return new LogicalExprStrategy(sanitized);
        }
        if (NULL_CHECK.matcher(lower).find()) {
            return new NullCheckStrategy(sanitized);
        }
        if (INSTANCE_OF.matcher(lower).find()) {
            return new InstanceOfStrategy(sanitized);
        }
        return new GenericUnsafeStrategy(sanitized);
    }
}
