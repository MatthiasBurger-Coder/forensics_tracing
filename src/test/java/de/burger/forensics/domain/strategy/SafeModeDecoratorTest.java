package de.burger.forensics.domain.strategy;

import de.burger.forensics.domain.model.RuleId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeModeDecoratorTest {

    @Test
    void wrapsDelegateExpressionWithSafeEvalCall() {
        ConditionStrategy delegate = () -> "value > 0";
        SafeModeDecorator decorator = new SafeModeDecorator(delegate, "org.example.SafeEval", "rule-1");

        assertThat(decorator.toBytemanIf())
            .isEqualTo("org.example.SafeEval.eval(\"rule-1\",\"value > 0\",(value > 0))");
    }

    @Test
    void escapesQuotesAndBackslashes() {
        ConditionStrategy delegate = () -> "text\\\"";
        SafeModeDecorator decorator = new SafeModeDecorator(delegate, "org.example.SafeEval", "rule-2");

        assertThat(decorator.toBytemanIf())
            .contains("text\\\\\\\"");
    }

    @Test
    void rejectsNullArguments() {
        ConditionStrategy delegate = () -> "true";

        assertThatThrownBy(() -> new SafeModeDecorator(null, "h", "id"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SafeModeDecorator(delegate, null, "id"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SafeModeDecorator(delegate, "h", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void safeModeUtilityWrapsDelegate() {
        ConditionStrategy delegate = () -> "flag";

        ConditionStrategy wrapped = SafeMode.wrap(delegate, "org.example.SafeEval", new RuleId("abc"));

        assertThat(wrapped.toBytemanIf())
            .contains("org.example.SafeEval.eval")
            .contains("abc");
    }

    @Test
    void returnsBlankWhenDelegateIsBlank() {
        ConditionStrategy delegate = () -> "  ";
        SafeModeDecorator decorator = new SafeModeDecorator(delegate, "org.example.SafeEval", "rule-blank");

        assertThat(decorator.toBytemanIf()).isBlank();

        ConditionStrategy wrapped = SafeMode.wrap(delegate, "org.example.SafeEval", new RuleId("rule-blank"));
        assertThat(wrapped.toBytemanIf()).isBlank();
    }
}
