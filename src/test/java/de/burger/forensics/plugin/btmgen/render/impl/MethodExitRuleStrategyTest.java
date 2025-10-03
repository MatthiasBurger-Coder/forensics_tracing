package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodExitRuleStrategyTest {

    @Test
    void rendersVoidExitUsingOnExitWithNullResult() {
        RuleParams params = new RuleParams(
                "ruleId",
                "com.example.Foo",
                "doWork",
                "()V",
                null,
                null,
                null
        );

        String rule = new MethodExitRuleStrategy().render(params);

        assertThat(rule)
                .contains("de.burger.forensics.infrastructure.rt.RtTrace.onExit(com.example.Foo.class, \"doWork\", null);")
                .doesNotContain("onExitVoid");
    }
}
