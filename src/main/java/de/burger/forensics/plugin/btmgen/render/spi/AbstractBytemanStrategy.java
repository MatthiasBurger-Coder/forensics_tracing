package de.burger.forensics.plugin.btmgen.render.spi;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    protected static int requireSourceLine(RuleParams params, String templateId) {
        int sourceLine = params.sourceLine();
        if (sourceLine > 0) {
            return sourceLine;
        }

        String className = (params.className() == null || params.className().isBlank()) ? "<unknown>" : params.className();
        String methodName = (params.methodName() == null || params.methodName().isBlank()) ? "<method>" : params.methodName();
        throw new IllegalArgumentException(
                templateId + " rule requires a valid source line for " + className + "#" + methodName
        );
    }

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

    protected static String resolveClassPlaceholder(String className, String expression) {
        if (expression == null) {
            return null;
        }
        if (className == null || className.isBlank()) {
            return expression;
        }
        return expression.replace("$CLASS.", className + ".");
    }

    protected static String qualifyStaticNullCheck(String className, String condition) {
        if (className == null || className.isBlank()) {
            return condition;
        }
        if (condition == null) {
            return null;
        }

        Matcher matcher = STATIC_NULL_CHECK.matcher(condition.trim());
        if (!matcher.matches()) {
            return condition;
        }

        String identifier = matcher.group("identifier");
        String operator = matcher.group("operator");
        return "%s.%s %s null".formatted(className, identifier, operator);
    }

    private static boolean isEnableLogPlaceholder(String expression) {
        String candidate = unwrapParentheses(expression);

        if (candidate.startsWith("!")) {
            candidate = unwrapParentheses(candidate.substring(1).trim());
        }

        return "ENABLE_LOG".equals(candidate);
    }

    private static final Pattern STATIC_NULL_CHECK = Pattern.compile(
            "^\\(?\\s*(?<identifier>[A-Z][A-Z0-9_]*)\\s*(?<operator>==|!=)\\s*null\\s*\\)?$"
    );

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
        // third argument must be a plain boolean expression; Byteman does not support lambda syntax
        return "eval(\"%s\", \"%s\", %s)".formatted(escapedRuleId, escapedExpr, evalExpression);
    }
}
