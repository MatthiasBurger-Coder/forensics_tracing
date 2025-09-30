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
        final String s = rawExpression.trim();
        final String l = s.toLowerCase(Locale.ROOT);

        if (LOGICAL.matcher(s).find()) {
            return new LogicalExprStrategy(s);
        }
        if (NULL_CHECK.matcher(l).find()) {
            return new NullCheckStrategy(s);
        }
        if (INSTANCE_OF.matcher(l).find()) {
            return new InstanceOfStrategy(s);
        }
        return new GenericUnsafeStrategy(s);
    }
}
