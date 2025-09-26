package de.burger.forensics.plugin.strategy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanCompositeStrategyTest {

    @Test
    void rendersWithParentheses() {
        ConditionStrategy a = new OriginalExpressionStrategy("A");
        ConditionStrategy b = new OriginalExpressionStrategy("B");
        ConditionStrategy c = new OriginalExpressionStrategy("C");

        ConditionStrategy and = new BooleanCompositeStrategy(BooleanCompositeStrategy.Op.AND, List.of(a, b));
        ConditionStrategy or = new BooleanCompositeStrategy(BooleanCompositeStrategy.Op.OR, List.of(and, c));

        assertThat(or.toBytemanIf()).isEqualTo("((A) AND (B)) OR (C)");
    }

    @Test
    void emptyChildrenFallsBackToTrue() {
        ConditionStrategy strategy = new BooleanCompositeStrategy(BooleanCompositeStrategy.Op.AND, List.of());
        assertThat(strategy.toBytemanIf()).isEqualTo("true");
    }
}
