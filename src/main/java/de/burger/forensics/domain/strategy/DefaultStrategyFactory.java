package de.burger.forensics.domain.strategy;

import de.burger.forensics.domain.model.RuleTemplate;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristics-based factory. Keeps detection simple and fast.
 * Falls back to GenericUnsafeStrategy for unknown/complex expressions.
 */
public final class DefaultStrategyFactory implements StrategyFactory {
    private static final String RETURN_KEYWORD = "return";

    private static final Pattern NULL_CHECK =
            Pattern.compile("==\\s*null|!=\\s*null", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTANCE_OF =
            Pattern.compile("\\binstanceof\\b");
    private static final Pattern LOGICAL =
            Pattern.compile("&&|\\|\\|");
    private static final Pattern COMPARISON =
            Pattern.compile("==|!=|<=|>=|<|>");
    private static final Pattern BOOLEAN_LITERAL =
            Pattern.compile("(?i)\\b(true|false)\\b");

    @Override
    public ConditionStrategy from(String rawExpression, RuleTemplate template, String returnType) {
        if (rawExpression == null || rawExpression.isBlank()) {
            return new GenericUnsafeStrategy("true");
        }

        String sanitized = InstanceOfPatternSanitizer.sanitize(rawExpression.trim());
        sanitized = stripTrailingSemicolon(sanitized);

        boolean isReturnTemplate = template == RuleTemplate.RETURN;
        boolean isBooleanReturn = isBooleanReturnType(returnType);
        if (isReturnTemplate) {
            sanitized = stripReturnKeyword(sanitized);
            if (sanitized.isBlank()) {
                return new GenericUnsafeStrategy("true");
            }
            if (!isBooleanReturn) {
                return new GenericUnsafeStrategy("true");
            }
            return new GenericUnsafeStrategy("$!");
        }

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
        if (COMPARISON.matcher(sanitized).find() || BOOLEAN_LITERAL.matcher(lower).find()) {
            return new GenericUnsafeStrategy(sanitized);
        }
        return new GenericUnsafeStrategy(sanitized);
    }

    private static String stripTrailingSemicolon(String expression) {
        String result = expression;
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private static String stripReturnKeyword(String expression) {
        String result = expression;
        if (result.regionMatches(true, 0, RETURN_KEYWORD, 0, RETURN_KEYWORD.length())) {
            result = result.substring(RETURN_KEYWORD.length()).trim();
        }
        return result;
    }

    private static boolean isBooleanReturnType(String returnType) {
        if (returnType == null) {
            return false;
        }
        String normalized = returnType.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return "boolean".equals(normalized)
            || "java.lang.boolean".equals(normalized)
            || normalized.endsWith(".boolean");
    }

}
