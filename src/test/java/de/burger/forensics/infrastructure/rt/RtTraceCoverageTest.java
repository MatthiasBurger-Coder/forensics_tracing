package de.burger.forensics.infrastructure.rt;

import org.jboss.byteman.rule.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RtTraceCoverageTest {

    @BeforeAll
    static void enableRuntime() {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("forensics.rt.output");
        System.clearProperty("forensics.rt.logFilePath");
        System.clearProperty("forensics.btmgen.logFilePath");
        System.clearProperty("forensics.btmgen.logFile");
        System.clearProperty("forensics.rt.logToFile");
        System.clearProperty("forensics.btmgen.logToFile");
        MDC.clear();
    }

    @Test
    void helperDelegatesCoverRuntimeWrapperMethods() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        String output = captureStdout(() -> {
            helper.onEnter(RtTraceCoverageTest.class, "run", "a");
            helper.onExit(RtTraceCoverageTest.class, "run", "ok");
            helper.onBranch(RtTraceCoverageTest.class, "run", "IF_TRUE");
            helper.onSwitch(RtTraceCoverageTest.class, "run", "switch");
            helper.onCase(RtTraceCoverageTest.class, "run", "case");
            helper.onException(new IllegalStateException("boom"));
            helper.ioBegin("read", "db");
            helper.ioEnd("read", "db");
            helper.threadFork("worker-1");
            helper.threadJoin("worker-1");
        });

        assertThat(output)
            .contains("\"event\":\"METHOD_ENTER\"")
            .contains("\"event\":\"METHOD_EXIT\"")
            .contains("\"event\":\"BRANCH_TAKEN\"")
            .contains("\"event\":\"EXCEPTION_THROWN\"")
            .contains("\"event\":\"IO_BEGIN\"")
            .contains("\"event\":\"IO_END\"")
            .contains("\"event\":\"THREAD_FORK\"")
            .contains("\"event\":\"THREAD_JOIN\"");
    }

    @Test
    void correlationIdsAndSpanTokensRemainObservable() {
        String output = captureStdout(() -> {
            String correlationId = RtTrace.newCorrelationId();
            RtTrace.setCorrelationId(correlationId);
            RtSpanToken token = RtTrace.beginSpan("work");
            RtTrace.trace(RtEvent.CUSTOM, Map.of("payload", "value"));
            RtTrace.endSpan(token);
        });

        assertThat(RtTrace.correlationId()).startsWith("corr-");
        assertThat(output)
            .contains("\"event\":\"TIMER_START\"")
            .contains("\"event\":\"TIMER_END\"")
            .contains("\"payload\":\"value\"");
    }

    @Test
    void privatePropertyHelpersHonorPreferredAndLegacySettings() throws Exception {
        Method fileLogPath = RtTrace.class.getDeclaredMethod("fileLogPath");
        fileLogPath.setAccessible(true);
        Method legacyBooleanTrue = RtTrace.class.getDeclaredMethod("legacyBooleanTrue", String.class);
        legacyBooleanTrue.setAccessible(true);

        System.setProperty("forensics.rt.output", "logs/primary.json");
        assertThat(fileLogPath.invoke(null)).isEqualTo("logs/primary.json");

        System.clearProperty("forensics.rt.output");
        System.setProperty("forensics.rt.logFilePath", "logs/runtime.json");
        assertThat(fileLogPath.invoke(null)).isEqualTo("logs/runtime.json");

        System.clearProperty("forensics.rt.logFilePath");
        System.setProperty("forensics.btmgen.logFilePath", "logs/legacy-path.json");
        assertThat(fileLogPath.invoke(null)).isEqualTo("logs/legacy-path.json");

        System.clearProperty("forensics.btmgen.logFilePath");
        System.setProperty("forensics.btmgen.logFile", "logs/legacy-file.json");
        assertThat(fileLogPath.invoke(null)).isEqualTo("logs/legacy-file.json");

        System.clearProperty("forensics.btmgen.logFile");
        System.setProperty("forensics.rt.logToFile", "true");
        assertThat(fileLogPath.invoke(null)).isEqualTo("logs/trace.json");
        assertThat(legacyBooleanTrue.invoke(null, "forensics.rt.logToFile")).isEqualTo(true);
    }

    @Test
    void conditionErrorAndBranchHelpersEscapeSpecialCharacters() {
        String output = captureStdout(() -> {
            RtTrace.branch("rule:\"1\"", "line\nvalue");
            RtTrace.conditionError("rule-1", "a\tb", new IllegalArgumentException("bad"));
        });

        assertThat(output)
            .contains("rule:\\\"1\\\"")
            .contains("line\\nvalue")
            .contains("a\\tb");
    }

    @Test
    void clearsThreadLocalStateAfterLastSpanEnds() throws Exception {
        @SuppressWarnings("unchecked")
        ThreadLocal<Object> spans = (ThreadLocal<Object>) getStaticField("SPANS");
        @SuppressWarnings("unchecked")
        ThreadLocal<String> correlationIds = (ThreadLocal<String>) getStaticField("CORR_ID");

        spans.remove();
        correlationIds.remove();

        Object firstStack = spans.get();
        captureStdout(() -> {
            RtTrace.setCorrelationId("corr-cleanup");
            RtSpanToken token = RtTrace.beginSpan("cleanup");
            RtTrace.setCorrelationId(null);
            RtTrace.endSpan(token);
        });
        Object secondStack = spans.get();
        String followUp = captureStdout(() -> RtTrace.trace(RtEvent.CUSTOM, Map.of("check", "done")));

        assertThat(secondStack).isNotSameAs(firstStack);
        assertThat(followUp)
            .contains("\"event\":\"CUSTOM\"")
            .doesNotContain("\"correlationId\"")
            .doesNotContain("\"span\"");

        spans.remove();
        correlationIds.remove();
    }

    @Test
    void reloadedDisabledRuntimeCoversFastPathNoops() throws Exception {
        withRtTraceLoader(
            Map.of("forensics.rt.enabled", "false"),
            (loader, rtTraceClass) -> {
                String output = captureStdout(() -> {
                    try {
                        Object noop = noopToken(loader);
                        invoke(rtTraceClass, "setCorrelationId", new Class<?>[]{String.class}, "corr-disabled");
                        assertThat(invoke(rtTraceClass, "newCorrelationId", new Class<?>[0])).isNull();
                        assertThat(invoke(rtTraceClass, "beginSpan", new Class<?>[]{String.class}, "disabled"))
                            .isEqualTo(noop);
                        invoke(rtTraceClass, "endSpan", new Class<?>[]{load(loader, "de.burger.forensics.infrastructure.rt.RtSpanToken")}, new Object[]{null});
                        invoke(rtTraceClass, "trace", new Class<?>[]{load(loader, "de.burger.forensics.infrastructure.rt.RtEvent"), Map.class}, customEvent(loader), Map.of("k", "v"));
                        invoke(rtTraceClass, "onEnter", new Class<?>[]{Class.class, String.class, Object[].class}, RtTraceCoverageTest.class, "run", new Object[]{"a"});
                        invoke(rtTraceClass, "onExit", new Class<?>[]{Class.class, String.class, Object.class}, RtTraceCoverageTest.class, "run", "ok");
                        invoke(rtTraceClass, "branch", new Class<?>[]{String.class, Object.class}, "rule", true);
                        invoke(rtTraceClass, "onBranch", new Class<?>[]{Class.class, String.class, String.class}, RtTraceCoverageTest.class, "run", "IF_TRUE");
                        invoke(rtTraceClass, "onSwitch", new Class<?>[]{Class.class, String.class, String.class}, RtTraceCoverageTest.class, "run", "switch");
                        invoke(rtTraceClass, "onCase", new Class<?>[]{Class.class, String.class, String.class}, RtTraceCoverageTest.class, "run", "case");
                        invoke(rtTraceClass, "conditionError", new Class<?>[]{String.class, String.class, Throwable.class}, "rule", "expr", new IllegalStateException("boom"));
                        invoke(rtTraceClass, "varSet", new Class<?>[]{String.class, Object.class}, "name", "value");
                        invoke(rtTraceClass, "onException", new Class<?>[]{Throwable.class}, new IllegalStateException("boom"));
                        invoke(rtTraceClass, "threadFork", new Class<?>[]{String.class}, "worker");
                        invoke(rtTraceClass, "threadJoin", new Class<?>[]{String.class}, "worker");
                        invoke(rtTraceClass, "lockAcquire", new Class<?>[]{String.class}, "lock");
                        invoke(rtTraceClass, "lockRelease", new Class<?>[]{String.class}, "lock");
                        invoke(rtTraceClass, "ioBegin", new Class<?>[]{String.class, String.class}, "read", "db");
                        invoke(rtTraceClass, "ioEnd", new Class<?>[]{String.class, String.class}, "read", "db");
                        invoke(rtTraceClass, "custom", new Class<?>[]{String.class, Map.class}, "custom", Map.of("payload", "value"));
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException(exception);
                    }
                });

                assertThat(output).isBlank();
                assertThat(invoke(rtTraceClass, "correlationId", new Class<?>[0])).isNull();
            }
        );
    }

    @Test
    void reloadedFileLoggingConfigurationCoversPrivateFileOutputBranches() throws Exception {
        Path logFile = Files.createTempDirectory("rt-trace").resolve("trace.json");
        try {
            withRtTraceLoader(
                Map.of(
                    "forensics.rt.enabled", "true",
                    "forensics.rt.output", logFile.toString()
                ),
                (loader, rtTraceClass) -> {
                    Method isFileLogEnabled = rtTraceClass.getDeclaredMethod("isFileLogEnabled");
                    isFileLogEnabled.setAccessible(true);
                    Method appendToFile = rtTraceClass.getDeclaredMethod("appendToFile", String.class);
                    appendToFile.setAccessible(true);

                    assertThat(isFileLogEnabled.invoke(null)).isEqualTo(true);
                    appendToFile.invoke(null, "{\"event\":\"CUSTOM\"}");
                }
            );

            assertThat(Files.readString(logFile)).contains("\"event\":\"CUSTOM\"");
        } finally {
            Files.deleteIfExists(logFile);
            Files.deleteIfExists(logFile.getParent());
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

    private static void withRtTraceLoader(
        Map<String, String> properties,
        RtTraceLoaderConsumer consumer
    ) throws Exception {
        Map<String, String> previous = new java.util.HashMap<>();
        properties.forEach((key, value) -> {
            previous.put(key, System.getProperty(key));
            System.setProperty(key, value);
        });
        try (URLClassLoader loader = new URLClassLoader(
            new URL[]{Path.of("build/classes/java/main").toAbsolutePath().toUri().toURL()},
            ClassLoader.getPlatformClassLoader()
        )) {
            consumer.accept(loader, load(loader, "de.burger.forensics.infrastructure.rt.RtTrace"));
        } finally {
            properties.forEach((key, value) -> {
                String oldValue = previous.get(key);
                if (oldValue == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, oldValue);
                }
            });
        }
    }

    private static Class<?> load(ClassLoader loader, String typeName) throws ClassNotFoundException {
        return Class.forName(typeName, true, loader);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object customEvent(ClassLoader loader) throws ClassNotFoundException {
        Class<? extends Enum> enumClass = (Class<? extends Enum>) load(loader, "de.burger.forensics.infrastructure.rt.RtEvent");
        return Enum.valueOf(enumClass, "CUSTOM");
    }

    private static Object noopToken(ClassLoader loader) throws ReflectiveOperationException {
        Field field = load(loader, "de.burger.forensics.infrastructure.rt.RtSpanToken").getDeclaredField("NOOP");
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object getStaticField(String fieldName) throws ReflectiveOperationException {
        Field field = RtTrace.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object invoke(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... args)
        throws ReflectiveOperationException {
        Method method = type.getMethod(methodName, parameterTypes);
        return method.invoke(null, args);
    }

    @FunctionalInterface
    private interface RtTraceLoaderConsumer {
        void accept(ClassLoader loader, Class<?> rtTraceClass) throws Exception;
    }
}
