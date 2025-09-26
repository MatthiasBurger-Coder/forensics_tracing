package de.burger.forensics.plugin.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FactoryDecoratorInteropTest {
    private static final String HELPER = "org.example.trace.SafeEval";
    private final StrategyFactory factory = new DefaultStrategyFactory();

    @Test
    void rawUnsafeExpressionUsesHelperWhenSafeModeEnabled() {
        ConditionStrategy base = factory.from("x != null && x.equals(\"OK\")");
        ConditionStrategy decorated = new SafeModeDecorator(base, true, false, HELPER, "id1");
        assertThat(decorated.toBytemanIf()).isEqualTo(HELPER + ".ifMatch(\"id1\")");
    }

    @Test
    void equalsLiteralRemainsInlineWhenSafeModeEnabled() {
        ConditionStrategy base = factory.from("user.status == \"OK\"");
        ConditionStrategy decorated = new SafeModeDecorator(base, true, false, HELPER, "id2");
        assertThat(decorated.toBytemanIf()).isEqualTo("user.status == \"OK\"");
    }
}
