// de.burger.forensics.plugin.btmgen.render.impl.IfFalseRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class IfFalseRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "IF_FALSE"; }
    @Override public String render(RuleParams p) {
        String cond = (p.condition() == null || p.condition().isBlank()) ? "false" : "!(" + p.condition() + ")";
        return """
            RULE %s : if-false %s#%s
            CLASS %s
            METHOD %s
            AT ENTRY
            IF %s
            DO
                de.burger.forensics.infrastructure.rt.RtTrace.onBranch(%s.class, "%s", "IF_FALSE");
            ENDRULE
            """.formatted(
                safeId(p.id()), or(p.displayName(), p.className()), p.methodName(),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                cond,
                p.className(), p.methodName()
        );
    }
}
