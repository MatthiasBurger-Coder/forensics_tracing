// de.burger.forensics.plugin.btmgen.render.impl.IfTrueRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class IfTrueRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "IF_TRUE"; }
    @Override public String render(RuleParams p) {
        // condition in p.condition() – wenn leer, erzwingen wir true
        String ruleId = safeId(p.id());
        String evaluation = (p.condition() == null || p.condition().isBlank()) ? "true" : p.condition();
        String cond = guardedCondition(ruleId, p.condition(), evaluation);
        return """
            RULE %s : if-true %s#%s
            CLASS %s
            METHOD %s
            HELPER %s
            AT ENTRY
            IF %s
            DO
                onBranch(%s.class, "%s", "IF_TRUE");
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
