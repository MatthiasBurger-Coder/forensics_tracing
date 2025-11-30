package de.burger.forensics.plugin.btmgen.render.spi;

import java.util.UUID;

public abstract class AbstractBytemanStrategy {
    protected static String safeId(String id) {
        return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }
    protected static String ifClause(String cond) {
        return (cond != null && !cond.isBlank()) ? "IF " + cond : "IF true";
    }
    protected static String or(String fallback, String value) {
        return (fallback != null && !fallback.isBlank()) ? fallback : value;
    }
    protected static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
    protected static String methodSig(String name, String desc) { return name + (desc != null ? desc : ""); }

    protected static String sanitizeCondition(String condition) {
        if (condition == null) {
            return null;
        }

        String trimmed = condition.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        if (isEnableLogPlaceholder(trimmed)) {
            return null;
        }

        return trimmed;
    }

    private static boolean isEnableLogPlaceholder(String expression) {
        String candidate = unwrapParentheses(expression);

        if (candidate.startsWith("!")) {
            candidate = unwrapParentheses(candidate.substring(1).trim());
        }

        return "ENABLE_LOG".equals(candidate);
    }

    private static String unwrapParentheses(String expression) {
        String result = expression;
        while (result.startsWith("(") && result.endsWith(")")) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    protected static String guardedCondition(String ruleId, String expression, String evaluation) {
        String evalExpression = (evaluation == null || evaluation.isBlank()) ? "false" : evaluation;
        String expr = (expression == null || expression.isBlank()) ? evalExpression : expression;
        String escapedRuleId = esc(ruleId);
        String escapedExpr = esc(expr);
        return "eval(\"%s\", \"%s\", %s)".formatted(escapedRuleId, escapedExpr, evalExpression);
    }
}
