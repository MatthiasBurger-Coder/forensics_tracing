package de.burger.forensics.plugin.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SafeModeDecoratorInteropTest {

    private static final StrategyFactory FACTORY = new DefaultStrategyFactory();
    private static final String H = "org.example.trace.SafeEval";

    @Test
    void safeMode_inline_for_whitelist_by_default() {
        ConditionStrategy base = FACTORY.from("user.status == \"OK\"");
        ConditionStrategy decorated = new SafeModeDecorator(base, true, false, H, "id1");
        assertThat(decorated.toBytemanIf()).isEqualTo("user.status == \"OK\"");
    }

    @Test
    void safeMode_force_helper_for_whitelist_when_flag_on() {
        ConditionStrategy base = FACTORY.from("user.status == \"OK\"");
        ConditionStrategy decorated = new SafeModeDecorator(base, true, true, H, "id2");
        assertThat(decorated.toBytemanIf())
            .isEqualTo("org.example.trace.SafeEval.ifEq(user.status, \"OK\")");
    }

    @Test
    void safeMode_unsafe_goes_to_helper() {
        ConditionStrategy base = FACTORY.from("x != null && x.equals(\"OK\")");
        ConditionStrategy decorated = new SafeModeDecorator(base, true, false, H, "id3");
        assertThat(decorated.toBytemanIf())
            .isEqualTo("org.example.trace.SafeEval.ifMatch(\"id3\")");
    }
}
