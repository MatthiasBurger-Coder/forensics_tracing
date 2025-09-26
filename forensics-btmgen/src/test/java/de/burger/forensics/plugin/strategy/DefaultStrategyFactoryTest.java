package de.burger.forensics.plugin.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStrategyFactoryTest {

    private final StrategyFactory factory = new DefaultStrategyFactory();

    @Test
    void equalsLiteralRecognized() {
        ConditionStrategy strategy = factory.from("user.status == \"OK\"");
        assertThat(strategy).isInstanceOf(EqualsLiteralStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("user.status == \"OK\"");
    }

    @Test
    void instanceofRecognized() {
        ConditionStrategy strategy = factory.from("obj instanceof com.acme.Type");
        assertThat(strategy).isInstanceOf(InstanceOfStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("obj instanceof com.acme.Type");
    }

    @Test
    void compositeAndOr() {
        ConditionStrategy strategy = factory.from("(a == 1) && (b instanceof X) || (c == 'Y')");
        assertThat(strategy).isInstanceOf(BooleanCompositeStrategy.class);
        assertThat(strategy.toBytemanIf()).contains("AND").contains("OR");
    }

    @Test
    void fallbackToOriginalExpression() {
        String raw = "x != null && x.equals(\"OK\")";
        ConditionStrategy strategy = factory.from(raw);
        assertThat(strategy).isInstanceOf(OriginalExpressionStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo(raw);
    }

    @Test
    void blankBecomesTrue() {
        ConditionStrategy strategy = factory.from("   ");
        assertThat(strategy.toBytemanIf()).isEqualTo("true");
    }
}
