package de.burger.forensics.infrastructure.rt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RtTracerTest {

    static {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void spanAutoCloseableEndsSpan() {
        RtTracer tracer = new RtTracer();

        String output = RtTraceLogCapture.capture(() -> {
            try (AutoCloseable span = tracer.span("work")) {
                assertThat(span).isNotNull();
                tracer.enter(RtTracerTest.class, "spanAutoCloseableEndsSpan");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(output)
            .contains("\"event\":\"TIMER_START\"")
            .contains("\"event\":\"TIMER_END\"")
            .contains("\"event\":\"METHOD_ENTER\"");
    }
}
