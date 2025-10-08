package de.burger.forensics.domain.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStrategyFactoryTest {

    private final StrategyFactory factory = new DefaultStrategyFactory();

    @Test
    void selectsNullCheckStrategyWhenExpressionContainsNullCheck() {
        ConditionStrategy strategy = factory.from("value == null");
        assertThat(strategy).isInstanceOf(NullCheckStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("value == null");
    }

    @Test
    void selectsLogicalStrategyWhenExpressionContainsLogicalOperators() {
        ConditionStrategy strategy = factory.from("a && b || c");
        assertThat(strategy).isInstanceOf(LogicalExprStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("a && b || c");
    }

    @Test
    void selectsInstanceOfStrategyWhenExpressionContainsInstanceOf() {
        ConditionStrategy strategy = factory.from("value instanceof String");
        assertThat(strategy).isInstanceOf(InstanceOfStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("value instanceof String");
    }

    @Test
    void sanitizesPatternInstanceOfExpressions() {
        ConditionStrategy strategy = factory.from("orderId instanceof OrderId orderId");
        assertThat(strategy).isInstanceOf(InstanceOfStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("orderId instanceof OrderId");
    }

    @Test
    void returnsGenericStrategyForBlankExpressions() {
        ConditionStrategy strategy = factory.from("   ");
        assertThat(strategy).isInstanceOf(GenericUnsafeStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("true");
    }

    @Test
    void fallsBackToGenericStrategyOtherwise() {
        ConditionStrategy strategy = factory.from("counter > 0");
        assertThat(strategy).isInstanceOf(GenericUnsafeStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("counter > 0");
    }
}
