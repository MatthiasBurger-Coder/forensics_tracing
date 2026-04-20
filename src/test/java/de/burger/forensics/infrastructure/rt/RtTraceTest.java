package de.burger.forensics.infrastructure.rt;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RtTraceTest {

    static {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void beginAndEndSpanEmitTimerEvents() {
        String output = RtTraceLogCapture.capture(() -> {
            RtSpanToken token = RtTrace.beginSpan("load");
            RtTrace.endSpan(token);
        });

        String[] lines = output.trim().split("\\R");
        assertThat(lines).hasSize(2);
        assertThat(lines[0])
            .contains("\"event\":\"TIMER_START\"")
            .contains("\"name\":\"load\"");
        assertThat(lines[1])
            .contains("\"event\":\"TIMER_END\"")
            .contains("\"durationNanos\"");
    }

    @Test
    void traceIncludesCorrelationIdWhenSet() {
        String output = RtTraceLogCapture.capture(() -> {
            RtTrace.setCorrelationId("corr-123");
            RtTrace.trace(RtEvent.CUSTOM, Map.of("k", "v"));
        });

        assertThat(output)
            .contains("\"event\":\"CUSTOM\"")
            .contains("\"correlationId\":\"corr-123\"")
            .contains("\"k\":\"v\"");
    }

    @Test
    void varSetUsesSafeStringWhenToStringFails() {
        Object bad = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("nope");
            }
        };

        String output = RtTraceLogCapture.capture(() -> RtTrace.varSet("answer", bad));

        assertThat(output)
            .contains("\"event\":\"VAR_SET\"")
            .contains("\"value\":\"<unprintable>\"");
    }

    @Test
    void onExceptionEmitsErrorDetails() {
        String output = RtTraceLogCapture.capture(() -> RtTrace.onException(new IllegalStateException("boom")));

        assertThat(output)
            .contains("\"event\":\"EXCEPTION_THROWN\"")
            .contains("\"error\":\"java.lang.IllegalStateException\"")
            .contains("\"errorMsg\":\"boom\"");
    }
}
