package de.burger.forensics.domain.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceOfPatternSanitizerTest {

    @Test
    void keepsClassicInstanceOfExpressions() {
        assertThat(InstanceOfPatternSanitizer.sanitize("value instanceof String"))
            .isEqualTo("value instanceof String");
    }

    @Test
    void stripsPatternBindingVariable() {
        assertThat(InstanceOfPatternSanitizer.sanitize("orderId instanceof OrderId orderId"))
            .isEqualTo("orderId instanceof OrderId");
    }

    @Test
    void handlesGenericTypes() {
        String expr = "value instanceof java.util.Map.Entry<String, Integer> entry";
        assertThat(InstanceOfPatternSanitizer.sanitize(expr))
            .isEqualTo("value instanceof java.util.Map.Entry<String, Integer>");
    }
}
