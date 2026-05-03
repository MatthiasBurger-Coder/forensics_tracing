// de.burger.forensics.plugin.btmgen.render.impl.SwitchRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class SwitchRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "SWITCH"; }
    @Override public String render(RuleParams p) {
        String selector = selectorMetadata(p);
        int sourceLine = requireSourceLine(p, id());
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
                "AT LINE " + sourceLine,
                p.className(), p.methodName(),
                selector
        );
    }

    private static String selectorMetadata(RuleParams p) {
        String selector = optionalLabel(p.condition(), p.eventLabel());
        return selector == null ? "\"\"" : "\"" + esc(selector) + "\"";
    }
}
