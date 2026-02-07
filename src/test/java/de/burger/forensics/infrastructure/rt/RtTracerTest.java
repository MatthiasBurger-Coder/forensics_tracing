package de.burger.forensics.infrastructure.rt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RtTracerTest {

    static {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void spanAutoCloseableEndsSpan() throws Exception {
        RtTracer tracer = new RtTracer();

        String output = captureStdout(() -> {
            try (AutoCloseable span = tracer.span("work")) {
                assertThat(span).isNotNull();
                tracer.enter(RtTracerTest.class, "spanAutoCloseableEndsSpan");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(output).contains("\"event\":\"TIMER_START\"");
        assertThat(output).contains("\"event\":\"TIMER_END\"");
        assertThat(output).contains("\"event\":\"METHOD_ENTER\"");
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
