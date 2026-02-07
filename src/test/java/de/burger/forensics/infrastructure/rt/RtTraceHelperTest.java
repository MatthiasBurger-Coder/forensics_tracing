package de.burger.forensics.infrastructure.rt;

import org.jboss.byteman.rule.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RtTraceHelperTest {

    @BeforeAll
    static void enableRuntime() {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void evalReturnsValueFromSupplier() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        boolean result = helper.eval("rule-1", "flag", () -> true);

        assertThat(result).isTrue();
    }

    @Test
    void evalPropagatesFalseValueFromBooleanOverload() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        boolean result = helper.eval("rule-2", "flag", false);

        assertThat(result).isFalse();
    }

    @Test
    void evalHandlesSupplierException() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        AtomicBoolean result = new AtomicBoolean(true);
        String output = captureStdout(() ->
                result.set(helper.eval("rule-3", "flag", () -> { throw new IllegalStateException("boom"); })));

        assertThat(result.get()).isFalse();
        assertThat(output).contains("\"event\":\"CONDITION_ERROR\"");
        assertThat(output).contains("\"rule\":\"rule-3\"");
    }

    @Test
    void mdcAndCorrelationIdReturnStoredValues() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        MDC.put("correlationId", "corr-xyz");
        MDC.put("customKey", "customValue");
        try {
            assertThat(helper.correlationId()).isEqualTo(MDC.get("correlationId"));
            assertThat(helper.mdc("customKey")).isEqualTo(MDC.get("customKey"));
        } finally {
            MDC.clear();
        }
    }

    private static String captureStdout(Runnable runnable) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream replacement = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(replacement);
            runnable.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
