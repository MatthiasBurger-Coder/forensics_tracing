package de.burger.forensics.infrastructure.logging;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodLoggingAspectAdditionalTest {

    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^.*\\] \\[cid=.*?\\] (.*)$");

    @AfterEach
    void tearDown() {
        System.clearProperty("forensics.btmgen.logToFile");
        System.clearProperty("forensics.btmgen.logFile");
        MDC.clear();
        clearStartStack();
    }

    @Test
    void aspectAccessorsExposeTheSingletonInstance() {
        assertThat(MethodLoggingAspect.aspectOf()).isSameAs(MethodLoggingAspect.aspectOf());
        assertThat(MethodLoggingAspect.hasAspect()).isTrue();
    }

    @Test
    void onEnterCreatesCorrelationIdsAndRendersNullArguments(@TempDir Path tempDir) throws Exception {
        Path logFile = tempDir.resolve("forensics.log");
        System.setProperty("forensics.btmgen.logToFile", "true");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
        MDC.put("cid", " ");
        MethodLoggingAspect aspect = new MethodLoggingAspect();

        aspect.onEnter(joinPoint("TestTarget.nulls(..)", (Object) null));

        assertThat(readMessages(logFile)).anyMatch(message -> message.contains("[null]"));
        assertThat(PluginLogger.getLogger(MethodLoggingAspectAdditionalTest.class).getName())
            .isEqualTo(MethodLoggingAspectAdditionalTest.class.getName());
    }

    @Test
    void onThrowLogsFailuresWithTheThrownMessage(@TempDir Path tempDir) throws Exception {
        Path logFile = tempDir.resolve("forensics.log");
        System.setProperty("forensics.btmgen.logToFile", "true");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
        MethodLoggingAspect aspect = new MethodLoggingAspect();
        JoinPoint jp = joinPoint("TestTarget.fail(..)");

        aspect.onEnter(jp);
        aspect.onThrow(jp, new IllegalStateException("boom"));

        assertThat(readMessages(logFile))
            .anyMatch(message -> message.contains("failed in"))
            .anyMatch(message -> message.contains("boom"));
    }

    @Test
    void onThrowWarnsWhenTimingStackIsUnbalanced(@TempDir Path tempDir) throws Exception {
        Path logFile = tempDir.resolve("forensics.log");
        System.setProperty("forensics.btmgen.logToFile", "true");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
        MethodLoggingAspect aspect = new MethodLoggingAspect();

        aspect.onThrow(joinPoint("TestTarget.orphan(..)"), new IllegalStateException("boom"));

        assertThat(readMessages(logFile)).anyMatch(message -> message.contains("(unbalanced timing stack)"));
    }

    @Test
    void fileLoggingCanBeDisabled(@TempDir Path tempDir) {
        Path logFile = tempDir.resolve("disabled.log");
        System.setProperty("forensics.btmgen.logToFile", "false");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
        MethodLoggingAspect aspect = new MethodLoggingAspect();

        aspect.onEnter(joinPoint("TestTarget.disabled(..)", "value"));

        assertThat(Files.exists(logFile)).isFalse();
    }

    @Test
    void fileLoggingCreatesParentDirectories(@TempDir Path tempDir) {
        Path logFile = tempDir.resolve("nested/logs/forensics.log");
        System.setProperty("forensics.btmgen.logToFile", "true");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
        MethodLoggingAspect aspect = new MethodLoggingAspect();

        aspect.onEnter(joinPoint("TestTarget.mkdir(..)", "value"));
        aspect.onReturn(joinPoint("TestTarget.mkdir(..)", "value"));

        assertThat(Files.exists(logFile)).isTrue();
    }

    @Test
    void fileLoggingAlsoWorksForPathsWithoutParent() {
        Path logFile = Path.of("forensics-btmgen-flat.log");
        System.setProperty("forensics.btmgen.logToFile", "true");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
        MethodLoggingAspect aspect = new MethodLoggingAspect();

        try {
            aspect.onEnter(joinPoint("TestTarget.flat(..)", "value"));

            assertThat(Files.exists(logFile)).isTrue();
        } finally {
            try {
                Files.deleteIfExists(logFile);
            } catch (Exception ignored) {
                // Best-effort cleanup for a flat temporary log file.
            }
        }
    }

    private static JoinPoint joinPoint(String shortString, Object... args) {
        JoinPoint jp = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(jp.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(MethodLoggingAspectAdditionalTest.class);
        when(signature.toShortString()).thenReturn(shortString);
        when(jp.getArgs()).thenReturn(args);
        return jp;
    }

    private static List<String> readMessages(Path logFile) throws Exception {
        return Files.readAllLines(logFile).stream()
            .map(MethodLoggingAspectAdditionalTest::extractMessage)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private static String extractMessage(String line) {
        Matcher matcher = MESSAGE_PATTERN.matcher(line);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static void clearStartStack() {
        try {
            Field field = MethodLoggingAspect.class.getDeclaredField("START_STACK");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<java.util.ArrayDeque<Long>> threadLocal =
                (ThreadLocal<java.util.ArrayDeque<Long>>) field.get(null);
            threadLocal.remove();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
