package de.burger.forensics.plugin.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyHelperRenderTest {

    private static final String H = "org.example.trace.SafeEval";

    @Test
    void equals_literal_renders_ifEq() {
        ConditionStrategy strategy = new EqualsLiteralStrategy("user.status", "\"OK\"");
        assertThat(strategy.toHelperIf(H, "rid"))
            .isEqualTo("org.example.trace.SafeEval.ifEq(user.status, \"OK\")");
    }

    @Test
    void instanceof_renders_ifInstanceOf() {
        ConditionStrategy strategy = new InstanceOfStrategy("obj", "com.acme.Foo");
        assertThat(strategy.toHelperIf(H, "rid"))
            .isEqualTo("org.example.trace.SafeEval.ifInstanceOf(obj, \"com.acme.Foo\")");
    }

    @Test
    void composite_builds_nested_and_or() {
        ConditionStrategy a = new EqualsLiteralStrategy("a", "1");
        ConditionStrategy b = new InstanceOfStrategy("b", "X");
        ConditionStrategy c = new EqualsLiteralStrategy("c", "'Y'");
        ConditionStrategy andStrategy =
            new BooleanCompositeStrategy(BooleanCompositeStrategy.Op.AND, List.of(a, b));
        ConditionStrategy orStrategy =
            new BooleanCompositeStrategy(BooleanCompositeStrategy.Op.OR, List.of(andStrategy, c));
        String expected = H + ".or(" + H + ".and(" + H + ".ifEq(a, 1), " + H
            + ".ifInstanceOf(b, \"X\")), " + H + ".ifEq(c, 'Y'))";
        assertThat(orStrategy.toHelperIf(H, "rid")).isEqualTo(expected);
    }

    @Test
    void original_expression_falls_back_to_ifMatch() {
        ConditionStrategy strategy = new OriginalExpressionStrategy("x != null && check(x)");
        assertThat(strategy.toHelperIf(H, "abc"))
            .isEqualTo("org.example.trace.SafeEval.ifMatch(\"abc\")");
    }
}
