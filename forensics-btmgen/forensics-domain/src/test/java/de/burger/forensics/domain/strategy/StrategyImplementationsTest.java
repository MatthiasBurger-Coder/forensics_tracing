package de.burger.forensics.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StrategyImplementationsTest {

    @Test
    void genericStrategyReturnsRawExpression() {
        GenericUnsafeStrategy strategy = new GenericUnsafeStrategy("flag");
        assertThat(strategy.toBytemanIf()).isEqualTo("flag");
    }

    @Test
    void logicalStrategyReturnsRawExpression() {
        LogicalExprStrategy strategy = new LogicalExprStrategy("a && b");
        assertThat(strategy.toBytemanIf()).isEqualTo("a && b");
    }

    @Test
    void nullCheckStrategyReturnsRawExpression() {
        NullCheckStrategy strategy = new NullCheckStrategy("value != null");
        assertThat(strategy.toBytemanIf()).isEqualTo("value != null");
    }

    @Test
    void instanceOfStrategyReturnsRawExpression() {
        InstanceOfStrategy strategy = new InstanceOfStrategy("obj instanceof String");
        assertThat(strategy.toBytemanIf()).isEqualTo("obj instanceof String");
    }

    @Test
    void strategiesRejectNullInput() {
        assertThatThrownBy(() -> new GenericUnsafeStrategy(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LogicalExprStrategy(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NullCheckStrategy(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InstanceOfStrategy(null))
            .isInstanceOf(NullPointerException.class);
    }
}
