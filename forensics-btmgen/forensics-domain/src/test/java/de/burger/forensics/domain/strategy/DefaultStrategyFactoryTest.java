package de.burger.forensics.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultStrategyFactoryTest {

    private final StrategyFactory factory = new DefaultStrategyFactory();

    @Test
    void selectsNullCheckStrategyWhenExpressionContainsNullCheck() {
        ConditionStrategy strategy = factory.from("value == null");
        assertThat(strategy).isInstanceOf(NullCheckStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("value == null");
    }

    @Test
    void fallsBackToGenericStrategyOtherwise() {
        ConditionStrategy strategy = factory.from("counter > 0");
        assertThat(strategy).isInstanceOf(GenericUnsafeStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("counter > 0");
    }
}
