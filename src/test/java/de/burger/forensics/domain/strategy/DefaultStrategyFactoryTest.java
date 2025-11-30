package de.burger.forensics.domain.strategy;

import de.burger.forensics.domain.model.RuleTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStrategyFactoryTest {

    private final StrategyFactory factory = new DefaultStrategyFactory();

    @Test
    void selectsNullCheckStrategyWhenExpressionContainsNullCheck() {
        ConditionStrategy strategy = factory.from("value == null", RuleTemplate.IF_TRUE, null);
        assertThat(strategy).isInstanceOf(NullCheckStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("value == null");
    }

    @Test
    void selectsLogicalStrategyWhenExpressionContainsLogicalOperators() {
        ConditionStrategy strategy = factory.from("a && b || c", RuleTemplate.IF_TRUE, null);
        assertThat(strategy).isInstanceOf(LogicalExprStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("a && b || c");
    }

    @Test
    void selectsInstanceOfStrategyWhenExpressionContainsInstanceOf() {
        ConditionStrategy strategy = factory.from("value instanceof String", RuleTemplate.IF_TRUE, null);
        assertThat(strategy).isInstanceOf(InstanceOfStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("value instanceof String");
    }

    @Test
    void sanitizesPatternInstanceOfExpressions() {
        ConditionStrategy strategy = factory.from("orderId instanceof OrderId orderId", RuleTemplate.IF_TRUE, null);
        assertThat(strategy).isInstanceOf(InstanceOfStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("orderId instanceof OrderId");
    }

    @Test
    void returnsGenericStrategyForBlankExpressions() {
        ConditionStrategy strategy = factory.from("   ", RuleTemplate.IF_TRUE, null);
        assertThat(strategy).isInstanceOf(GenericUnsafeStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("true");
    }

    @Test
    void fallsBackToGenericStrategyOtherwise() {
        ConditionStrategy strategy = factory.from("counter > 0", RuleTemplate.IF_TRUE, null);
        assertThat(strategy).isInstanceOf(GenericUnsafeStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("counter > 0");
    }

    @Test
    void normalizesReturnExpressionsForNonBooleanMethods() {
        ConditionStrategy strategy = factory.from("return modern.sumGross($1);", RuleTemplate.RETURN, "double");

        assertThat(strategy).isInstanceOf(GenericUnsafeStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("true");
    }

    @Test
    void usesReturnPlaceholderForBooleanReturnExpressions() {
        ConditionStrategy strategy = factory.from("return isEnabled();", RuleTemplate.RETURN, "boolean");

        assertThat(strategy).isInstanceOf(GenericUnsafeStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("$!");
    }

    @Test
    void ignoresBooleanLiteralsInNonBooleanReturnExpressions() {
        String expression = "return new EnvTogglePolicy(readBool(\"order.new.enabled\", \"ORDER_NEW_ENABLED\", false),"
            + " readInt(\"order.new.percent\", \"ORDER_NEW_PERCENT\", 0));";

        ConditionStrategy strategy = factory.from(expression, RuleTemplate.RETURN, "com.acme.EnvTogglePolicy");

        assertThat(strategy).isInstanceOf(GenericUnsafeStrategy.class);
        assertThat(strategy.toBytemanIf()).isEqualTo("true");
    }
}
