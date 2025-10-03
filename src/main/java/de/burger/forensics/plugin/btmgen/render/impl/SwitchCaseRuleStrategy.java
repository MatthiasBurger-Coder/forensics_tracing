// de.burger.forensics.plugin.btmgen.render.impl.SwitchCaseRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class SwitchCaseRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "SWITCH_CASE"; }
    @Override public String render(RuleParams p) {
        // Nutze p.displayName() als Case-Label; alternativ eigenes Feld/Meta
        String label = p.displayName() == null ? "<case>" : esc(p.displayName());
        return """
            RULE %s : switch-case %s#%s
            CLASS %s
            METHOD %s
            AT ENTRY
            IF true
            DO
                de.burger.forensics.infrastructure.rt.RtTrace.onCase(%s.class, "%s", "%s");
            ENDRULE
            """.formatted(
                safeId(p.id()), or(p.displayName(), p.className()), p.methodName(),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.className(), p.methodName(), label
        );
    }
}
