// de.burger.forensics.plugin.btmgen.render.impl.MethodExitRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class MethodExitRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "METHOD_EXIT"; }
    @Override public String render(RuleParams p) {
        return """
            RULE %s : exit %s#%s
            CLASS %s
            METHOD %s
            AT EXIT
            %s
            DO
                %s.onExit(%s.class, "%s", null);
            ENDRULE
            """.formatted(
                safeId(p.id()), or(p.displayName(), p.className()), p.methodName(),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                ifClause(p.condition()),
                p.helperFqn(), p.className(), p.methodName()
        );
    }
}
