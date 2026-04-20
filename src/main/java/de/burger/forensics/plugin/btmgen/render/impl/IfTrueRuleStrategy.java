// de.burger.forensics.plugin.btmgen.render.impl.IfTrueRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

public final class IfTrueRuleStrategy extends AbstractIfRuleStrategy {
    @Override public String id() { return "IF_TRUE"; }

    @Override
    protected String branchLabel() {
        return "if-true";
    }

    @Override
    protected String booleanExpression(String condition) {
        return condition == null ? "true" : condition;
    }
}
