// de.burger.forensics.plugin.btmgen.render.impl.SwitchCaseRuleStrategy
package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class SwitchCaseRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "SWITCH_CASE"; }
    @Override public String render(RuleParams p) {
        String label = esc(optionalLabel(p.eventLabel(), "<case>"));
        int sourceLine = requireSourceLine(p, id());
        return """
            RULE %s : switch-case %s
            CLASS %s
            METHOD %s
            HELPER %s
            %s
            IF true
            DO
                onCase(%s.class, "%s", "%s");
            ENDRULE
            """.formatted(
                safeId(p.id()), ruleTarget(p),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.helperFqn(),
                "AT LINE " + sourceLine,
                p.className(), p.methodName(), label
        );
    }
}
