package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class ThrowRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "THROW"; }

    @Override public String render(RuleParams p) {
        return """
            RULE %s : throw %s
            CLASS %s
            METHOD %s
            HELPER %s
            AT THROW
            IF true
            DO
                onException($^);
            ENDRULE
            """.formatted(
                safeId(p.id()), ruleTarget(p),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.helperFqn()
        );
    }
}
