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
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new MethodExitRuleStrategy().render(params);

        assertThat(rule)
                .contains("HELPER de.burger.forensics.infrastructure.rt.RtTraceHelper")
                .contains("helper().onExit(com.example.Foo.class, \"doWork\", null);")
                .doesNotContain("onExitVoid");
    }

    @Test
    void usesProvidedHelperFqn() {
        RuleParams params = new RuleParams(
                "ruleId",
                "com.example.Foo",
                "doWork",
                "()V",
                null,
                null,
                null,
                "com.example.Helper"
        );

        String rule = new MethodExitRuleStrategy().render(params);

        assertThat(rule)
                .contains("HELPER com.example.Helper")
                .contains("helper().onExit(com.example.Foo.class, \"doWork\", null);");
    }
}
