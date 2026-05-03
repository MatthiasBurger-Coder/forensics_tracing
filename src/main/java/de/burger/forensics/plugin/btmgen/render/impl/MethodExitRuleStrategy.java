// de.burger.forensics.plugin.btmgen.render.impl.MethodExitRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class MethodExitRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "METHOD_EXIT"; }
    @Override public String render(RuleParams p) {
        return """
            RULE %s : exit %s
            CLASS %s
            METHOD %s
            HELPER %s
            AT EXIT
            %s
            DO
                onExit(%s.class, "%s", null);
            ENDRULE
            """.formatted(
                safeId(p.id()), ruleTarget(p),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.helperFqn(),
                ifClause(p.condition()),
                p.className(), p.methodName()
        );
    }
}
