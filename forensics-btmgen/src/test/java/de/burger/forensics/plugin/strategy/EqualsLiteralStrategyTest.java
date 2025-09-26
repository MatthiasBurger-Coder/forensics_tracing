package de.burger.forensics.plugin.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EqualsLiteralStrategyTest {

    @Test
    void rendersEquality() {
        ConditionStrategy strategy = new EqualsLiteralStrategy("value", "\"OK\"");
        assertThat(strategy.toBytemanIf()).isEqualTo("value == \"OK\"");
    }
}
