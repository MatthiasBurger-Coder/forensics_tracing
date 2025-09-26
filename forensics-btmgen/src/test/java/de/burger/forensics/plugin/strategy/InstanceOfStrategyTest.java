package de.burger.forensics.plugin.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceOfStrategyTest {

    @Test
    void rendersInstanceOf() {
        ConditionStrategy strategy = new InstanceOfStrategy("obj", "com.example.Type");
        assertThat(strategy.toBytemanIf()).isEqualTo("obj instanceof com.example.Type");
    }
}
