package de.burger.forensics.infrastructure.rt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RtTraceTest {

    static {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void beginAndEndSpanEmitTimerEvents() {
        String output = captureStdout(() -> {
            RtSpanToken token = RtTrace.beginSpan("load");
            RtTrace.endSpan(token);
        });

        String[] lines = output.trim().split("\\R");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).contains("\"event\":\"TIMER_START\"");
        assertThat(lines[0]).contains("\"name\":\"load\"");
        assertThat(lines[1]).contains("\"event\":\"TIMER_END\"");
        assertThat(lines[1]).contains("\"durationNanos\"");
    }

    @Test
    void traceIncludesCorrelationIdWhenSet() {
        String output = captureStdout(() -> {
            RtTrace.setCorrelationId("corr-123");
            RtTrace.trace(RtEvent.CUSTOM, Map.of("k", "v"));
        });

        assertThat(output).contains("\"event\":\"CUSTOM\"");
        assertThat(output).contains("\"correlationId\":\"corr-123\"");
        assertThat(output).contains("\"k\":\"v\"");
    }

    @Test
    void varSetUsesSafeStringWhenToStringFails() {
        Object bad = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("nope");
            }
        };

        String output = captureStdout(() -> RtTrace.varSet("answer", bad));

        assertThat(output).contains("\"event\":\"VAR_SET\"");
        assertThat(output).contains("\"value\":\"<unprintable>\"");
    }

    @Test
    void onExceptionEmitsErrorDetails() {
        String output = captureStdout(() -> RtTrace.onException(new IllegalStateException("boom")));

        assertThat(output).contains("\"event\":\"EXCEPTION_THROWN\"");
        assertThat(output).contains("\"error\":\"java.lang.IllegalStateException\"");
        assertThat(output).contains("\"errorMsg\":\"boom\"");
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
