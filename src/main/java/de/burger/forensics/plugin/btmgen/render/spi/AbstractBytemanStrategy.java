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
    protected static String esc(String s) { return s == null ? "" : s.replace("\"", "\\\""); }
    protected static String methodSig(String name, String desc) { return name + (desc != null ? desc : ""); }

    protected static String guardedCondition(String ruleId, String expression, String evaluation) {
        String expr = (expression == null || expression.isBlank()) ? evaluation : expression;
        String escapedRuleId = esc(ruleId);
        String escapedExpr = esc(expr);
        return "eval(\"%s\", \"%s\", () -> %s)".formatted(escapedRuleId, escapedExpr, evaluation);
    }
}
