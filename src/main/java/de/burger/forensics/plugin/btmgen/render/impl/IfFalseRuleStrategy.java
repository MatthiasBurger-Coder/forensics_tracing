// de.burger.forensics.plugin.btmgen.render.impl.IfFalseRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class IfFalseRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "IF_FALSE"; }
    @Override public String render(RuleParams p) {
        String ruleId = safeId(p.id());
        String condition = sanitizeCondition(p.condition());
        String booleanExpr = (condition == null) ? "false" : "!(" + condition + ")";

        String exprString = booleanExpr;
        String cond = guardedCondition(ruleId, exprString, booleanExpr);
        return """
            RULE %s : if-false %s#%s
            CLASS %s
            METHOD %s
            HELPER %s
            AT ENTRY
            IF %s
            DO
                onBranch(%s.class, "%s", "IF_FALSE");
            ENDRULE
            """.formatted(
                ruleId, or(p.displayName(), p.className()), p.methodName(),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.helperFqn(),
                cond,
                p.className(), p.methodName()
        );
    }
}
