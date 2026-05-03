// de.burger.forensics.plugin.btmgen.render.impl.SwitchRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class SwitchRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "SWITCH"; }
    @Override public String render(RuleParams p) {
        String selector = selectorMetadata(p);
        return """
            RULE %s : switch %s
            CLASS %s
            METHOD %s
            HELPER %s
            %s
            IF true
            DO
                onSwitch(%s.class, "%s", %s );
            ENDRULE
            """.formatted(
                safeId(p.id()), ruleTarget(p),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.helperFqn(),
                atLineOrEntry(p.sourceLine()),
                p.className(), p.methodName(),
                selector
        );
    }

    private static String selectorMetadata(RuleParams p) {
        String selector = p.condition();
        if (selector == null || selector.isBlank()) {
            selector = p.displayName();
        }
        return selector == null ? "\"\"" : "\"" + esc(selector) + "\"";
    }
}
