package de.burger.forensics.infrastructure.rt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RtTraceAdditionalTest {

    @BeforeAll
    static void enableRuntime() {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void emitsBranchAndIoEventsWithEscapedPayloads() {
        String output = captureStdout(() -> {
            RtTrace.setCorrelationId("corr-101");
            RtTrace.onEnter(RtTraceAdditionalTest.class, "work", "value\"1", "line\nbreak");
            RtTrace.onExit(RtTraceAdditionalTest.class, "work", "done");
            RtTrace.onBranch(RtTraceAdditionalTest.class, "work", "then");
            RtTrace.onSwitch(RtTraceAdditionalTest.class, "work", "switch \"label\"");
            RtTrace.onCase(RtTraceAdditionalTest.class, "work", "case\n1");
            RtTrace.ioBegin("read", "/tmp/file.txt");
            RtTrace.ioEnd("read", "/tmp/file.txt");
            RtTrace.threadFork("worker-1");
            RtTrace.threadJoin("worker-1");
            RtTrace.lockAcquire("lock-1");
            RtTrace.lockRelease("lock-1");
            RtTrace.custom("custom", Map.of("payload", "line1\nline2"));
        });

        assertThat(output)
            .contains("\"event\":\"METHOD_ENTER\"")
            .contains("\"event\":\"METHOD_EXIT\"")
            .contains("\"event\":\"BRANCH_TAKEN\"")
            .contains("\"event\":\"IO_BEGIN\"")
            .contains("\"event\":\"IO_END\"")
            .contains("\"event\":\"THREAD_FORK\"")
            .contains("\"event\":\"THREAD_JOIN\"")
            .contains("\"event\":\"LOCK_ACQUIRE\"")
            .contains("\"event\":\"LOCK_RELEASE\"")
            .contains("\"event\":\"CUSTOM\"")
            .contains("value")
            .contains("line1")
            .contains("line2");
    }

    @Test
    void conditionErrorIncludesErrorDetails() {
        String output = captureStdout(
            () -> RtTrace.conditionError("rule-77", "x > 0", new IllegalArgumentException("bad"))
        );

        assertThat(output)
            .contains("\"event\":\"CONDITION_ERROR\"")
            .contains("\"rule\":\"rule-77\"")
            .contains("\"expression\":\"x > 0\"")
            .contains("\"error\":\"java.lang.IllegalArgumentException\"")
            .contains("\"errorMsg\":\"bad\"");
    }

    @Test
    void handlesEmptyDetailsAndSpanStackState() {
        String output = captureStdout(() -> {
            RtSpanToken token = RtTrace.beginSpan("work");
            RtTrace.trace(RtEvent.CUSTOM, Map.of());
            RtTrace.trace(RtEvent.CUSTOM, null);
            RtTrace.endSpan(token);
            RtTrace.endSpan(null);
            RtTrace.endSpan(RtSpanToken.NOOP);
            RtTrace.onEnter(null, "noop", (Object[]) null);
            RtTrace.onSwitch(RtTraceAdditionalTest.class, "noop", null);
            RtTrace.onCase(RtTraceAdditionalTest.class, "noop", null);
            RtTrace.trace(RtEvent.CUSTOM, Map.of("value", "tab\tvalue", "path", "C:\\temp"));
        });

        assertThat(output)
            .contains("\"details\":\"\"")
            .contains("\"span\"")
            .contains("\"class\":\"\"")
            .contains("tab\\tvalue")
            .contains("C:\\\\temp");
    }

    @Test
    void onExceptionHandlesNullAndEmptyStackTrace() {
        String output = captureStdout(() -> {
            RtTrace.onException(null);
            Throwable empty = new IllegalStateException("boom");
            empty.setStackTrace(new StackTraceElement[0]);
            RtTrace.onException(empty);
            RtTrace.setCorrelationId(null);
            RtTrace.newCorrelationId();
        });

        assertThat(output)
            .contains("\"event\":\"EXCEPTION_THROWN\"")
            .contains("\"topFrame\":\"\"");
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
