package de.burger.forensics.domain.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyImplementationsTest {

    @Test
    void genericStrategyReturnsRawExpression() {
        GenericUnsafeStrategy strategy = new GenericUnsafeStrategy("flag");
        assertThat(strategy.toBytemanIf()).isEqualTo("flag");
    }

    @Test
    void logicalStrategyNormalizesOperators() {
        // Already correct
        assertThat(new LogicalExprStrategy("a && b").toBytemanIf()).isEqualTo("a && b");
        assertThat(new LogicalExprStrategy("x || y").toBytemanIf()).isEqualTo("x || y");
        // Single operators are normalized
        assertThat(new LogicalExprStrategy("a & b").toBytemanIf()).isEqualTo("a && b");
        assertThat(new LogicalExprStrategy("x | y").toBytemanIf()).isEqualTo("x || y");
        // Mixed spacing and parentheses
        assertThat(new LogicalExprStrategy(" (a&b) | c ").toBytemanIf()).isEqualTo("(a&&b) || c");
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
