package de.burger.forensics.plugin.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeModeDecoratorTest {

    @Test
    void wrapsWithSafeEval() {
        ConditionStrategy base = () -> "x != null && x.isOk()";
        ConditionStrategy safe = new SafeModeDecorator(base, "org.example.trace.SafeEval", "r#abc123");

        String expr = safe.toBytemanIf();
        assertThat(expr).contains("org.example.trace.SafeEval.eval(");
        assertThat(expr).contains("\"r#abc123\"");
        assertThat(expr).contains("x != null && x.isOk()");
    }
}
