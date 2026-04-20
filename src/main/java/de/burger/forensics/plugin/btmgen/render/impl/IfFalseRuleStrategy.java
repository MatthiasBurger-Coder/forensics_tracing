// de.burger.forensics.plugin.btmgen.render.impl.IfFalseRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

public final class IfFalseRuleStrategy extends AbstractIfRuleStrategy {
    @Override public String id() { return "IF_FALSE"; }

    @Override
    protected String branchLabel() {
        return "if-false";
    }

    @Override
    protected String booleanExpression(String condition) {
        return condition == null ? "false" : "!(" + condition + ")";
    }
}
