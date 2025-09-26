package de.burger.forensics.plugin.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalExpressionStrategyTest {

    @Test
    void passThrough() {
        String raw = "a && b || c";
        ConditionStrategy strategy = new OriginalExpressionStrategy(raw);
        assertThat(strategy.toBytemanIf()).isEqualTo(raw);
    }
}
