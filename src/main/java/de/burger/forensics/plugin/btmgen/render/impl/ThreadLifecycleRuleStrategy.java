package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class ThreadLifecycleRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "THREAD_LIFECYCLE"; }

    @Override public String render(RuleParams p) {
        String id = safeId(p.id());
        return """
            RULE %s-start : thread start
            CLASS java.lang.Thread
            METHOD start()
            AT ENTRY
            IF true
            DO
                de.burger.forensics.infrastructure.rt.RtTrace.threadFork($0.getName());
            ENDRULE

            RULE %s-join : thread join
            CLASS java.lang.Thread
            METHOD join(..)
            AT ENTRY
            IF true
            DO
                de.burger.forensics.infrastructure.rt.RtTrace.threadJoin($0.getName());
            ENDRULE
            """.formatted(id, id);
    }
}
