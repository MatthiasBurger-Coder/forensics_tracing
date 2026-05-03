package de.burger.forensics.infrastructure.rt;

import org.jboss.byteman.rule.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

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

        AtomicBoolean result = new AtomicBoolean(false);
        String output = RtTraceLogCapture.capture(() ->
                result.set(helper.eval("rule-1", "flag", () -> true)));

        assertThat(result.get()).isTrue();
        assertThat(output)
                .doesNotContain("\"event\":\"BRANCH_TAKEN\"")
                .doesNotContain("\"event\":\"CONDITION_ERROR\"");
    }

    @Test
    void evalPropagatesFalseValueFromBooleanOverload() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        AtomicBoolean result = new AtomicBoolean(true);
        String output = RtTraceLogCapture.capture(() ->
                result.set(helper.eval("rule-2", "flag", false)));

        assertThat(result.get()).isFalse();
        assertThat(output).doesNotContain("\"event\":\"BRANCH_TAKEN\"");
    }

    @Test
    void evalHandlesSupplierException() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        AtomicBoolean result = new AtomicBoolean(true);
        String output = RtTraceLogCapture.capture(() ->
                result.set(helper.eval("rule-3", "flag", () -> { throw new IllegalStateException("boom"); })));

        assertThat(result.get()).isFalse();
        assertThat(output)
            .contains("\"event\":\"CONDITION_ERROR\"")
            .contains("\"rule\":\"rule-3\"")
            .doesNotContain("\"event\":\"BRANCH_TAKEN\"");
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
}
