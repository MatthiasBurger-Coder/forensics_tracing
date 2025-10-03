// de.burger.forensics.plugin.btmgen.render.impl.SwitchRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class SwitchRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "SWITCH"; }
    @Override public String render(RuleParams p) {
        // p.displayName() optional als Label/Vorschau
        return """
            RULE %s : switch %s#%s
            CLASS %s
            METHOD %s
            AT ENTRY
            IF true
            DO
                de.burger.forensics.infrastructure.rt.RtTrace.onSwitch(%s.class, "%s", %s );
            ENDRULE
            """.formatted(
                safeId(p.id()), or(p.displayName(), p.className()), p.methodName(),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.className(), p.methodName(),
                p.displayName() == null ? "\"\"" : "\"" + esc(p.displayName()) + "\""
        );
    }
}
