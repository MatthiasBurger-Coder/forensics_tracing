package de.burger.forensics.plugin.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SafeModeDecoratorTest {
    private static final String HELPER = "org.example.trace.SafeEval";

    @Test
    void delegatesWhenSafeModeDisabled() {
        ConditionStrategy delegate = new OriginalExpressionStrategy("x != null && x.equals(\"OK\")");
        ConditionStrategy decorated = new SafeModeDecorator(delegate, false, false, HELPER, "abc");
        assertThat(decorated.toBytemanIf()).isEqualTo(delegate.toBytemanIf());
    }

    @Test
    void keepsEqualsLiteralInlineWhenSafeModeEnabled() {
        ConditionStrategy delegate = new EqualsLiteralStrategy("user.status", "\"OK\"");
        ConditionStrategy decorated = new SafeModeDecorator(delegate, true, false, HELPER, "rid1");
        assertThat(decorated.toBytemanIf()).isEqualTo("user.status == \"OK\"");
    }

    @Test
    void keepsInstanceOfInlineWhenSafeModeEnabled() {
        ConditionStrategy delegate = new InstanceOfStrategy("obj", "MyType");
        ConditionStrategy decorated = new SafeModeDecorator(delegate, true, false, HELPER, "rid2");
        assertThat(decorated.toBytemanIf()).isEqualTo("obj instanceof MyType");
    }

    @Test
    void keepsBooleanCompositeInlineWhenSafeModeEnabled() {
        ConditionStrategy left = new EqualsLiteralStrategy("user.status", "\"OK\"");
        ConditionStrategy right = new EqualsLiteralStrategy("user.role", "\"ADMIN\"");
        ConditionStrategy composite = new BooleanCompositeStrategy(
            BooleanCompositeStrategy.Op.AND,
            List.of(left, right)
        );
        ConditionStrategy decorated = new SafeModeDecorator(composite, true, false, HELPER, "rid3");
        assertThat(decorated.toBytemanIf())
            .isEqualTo("(user.status == \"OK\") AND (user.role == \"ADMIN\")");
    }

    @Test
    void routesUnsafeExpressionThroughHelperWhenSafeModeEnabled() {
        ConditionStrategy delegate = new OriginalExpressionStrategy("x != null && x.equals(\"OK\")");
        ConditionStrategy decorated = new SafeModeDecorator(delegate, true, false, HELPER, "deadbeef");
        assertThat(decorated.toBytemanIf()).isEqualTo(HELPER + ".ifMatch(\"deadbeef\")");
    }
}
