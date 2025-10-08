package de.burger.forensics.domain.strategy;

import de.burger.forensics.domain.model.RuleTemplate;

import java.util.Locale;
import java.util.Objects;
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
        if (isReturnTemplate) {
            sanitized = stripReturnKeyword(sanitized);
            if (sanitized.isBlank()) {
                return new GenericUnsafeStrategy("true");
            }
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
        if (isReturnTemplate && !isBooleanReturnType(returnType) && !looksBooleanExpression(sanitized)) {
            return new GenericUnsafeStrategy("true");
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
        if (result.regionMatches(true, 0, "return", 0, "return".length())) {
            result = result.substring("return".length()).trim();
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

    private static boolean looksBooleanExpression(String expression) {
        String trimmed = Objects.requireNonNull(expression).trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("!")) {
            return true;
        }
        if (BOOLEAN_LITERAL.matcher(trimmed).find()) {
            return true;
        }
        if (COMPARISON.matcher(trimmed).find()) {
            return true;
        }
        return false;
    }
}
