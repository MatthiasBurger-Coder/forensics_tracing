package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class ReturnRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "RETURN"; }

    @Override public String render(RuleParams p) {
        return """
            RULE %s : return %s#%s
            CLASS %s
            METHOD %s
            AT EXIT
            %s
            DO
                %s.onExit(%s.class, "%s", $! );
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
