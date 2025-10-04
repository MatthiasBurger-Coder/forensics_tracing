package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class MethodEnterRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "METHOD_ENTER"; }

    @Override public String render(RuleParams p) {
        return """
            RULE %s : enter %s#%s
            CLASS %s
            METHOD %s
            HELPER %s
            AT ENTRY
            %s
            DO
                onEnter(%s.class, "%s", $* );
            ENDRULE
            """.formatted(
                safeId(p.id()), or(p.displayName(), p.className()), p.methodName(),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.helperFqn(),
                ifClause(p.condition()),
                p.className(), p.methodName()
        );
    }
}
