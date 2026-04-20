package de.burger.forensics.infrastructure.logging;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodLoggingAspectTest {

    private static final Pattern OK_PATTERN = Pattern.compile("OK in (\\d+) ms");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^.*\\] \\[cid=.*?\\] (.*)$");

    private Path logFile;

    @BeforeEach
    void setUpLogging() throws Exception {
        logFile = Files.createTempFile("forensics-btmgen", "-" + UUID.randomUUID() + ".log");
        System.setProperty("forensics.btmgen.logToFile", "true");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
    }

    @AfterEach
    void tearDownLogging() throws Exception {
        System.clearProperty("forensics.btmgen.logToFile");
        System.clearProperty("forensics.btmgen.logFile");
        if (logFile != null) {
            Files.deleteIfExists(logFile);
        }
    }

    @Test
    void doesNotReportUptimeLikeDurationsForNestedCalls() {
        final MethodLoggingAspect aspect = new MethodLoggingAspect();
        final JoinPoint outer = joinPoint("TestTarget.outer(..)");
        final JoinPoint inner = joinPoint("TestTarget.inner(..)");

        aspect.onEnter(outer);
        waitAtLeastMillis(10);
        aspect.onEnter(inner);
        waitAtLeastMillis(5);
        aspect.onReturn(inner);
        waitAtLeastMillis(5);
        aspect.onReturn(outer);

        final List<Long> durations = readMessages().stream()
                .map(MethodLoggingAspectTest::extractDurationMs)
                .flatMap(java.util.Optional::stream)
                .toList();

        assertThat(durations)
                .hasSize(2)
                .allMatch(value -> value >= 0 && value < Duration.ofMinutes(1).toMillis());
    }

    @Test
    void cleansUpThreadLocalWhenStackIsEmpty() throws Exception {
        final MethodLoggingAspect aspect = new MethodLoggingAspect();
        final JoinPoint jp = joinPoint("TestTarget.single(..)");

        aspect.onEnter(jp);
        aspect.onReturn(jp);

        final ThreadLocal<ArrayDeque<Long>> threadLocal = getThreadLocal();
        assertThat(threadLocal.get()).isEmpty();
    }

    @Test
    void warnsOnUnbalancedReturn() {
        final MethodLoggingAspect aspect = new MethodLoggingAspect();
        final JoinPoint jp = joinPoint("TestTarget.unbalanced(..)");

        aspect.onReturn(jp);

        assertThat(readMessages())
                .anyMatch(message -> message.contains("(unbalanced timing stack)"));
    }

    private static JoinPoint joinPoint(String shortString, Object... args) {
        final JoinPoint jp = mock(JoinPoint.class);
        final Signature signature = mock(Signature.class);
        when(jp.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(TestTarget.class);
        when(signature.toShortString()).thenReturn(shortString);
        when(jp.getArgs()).thenReturn(args);
        return jp;
    }

    private static java.util.Optional<Long> extractDurationMs(String message) {
        final Matcher matcher = OK_PATTERN.matcher(message);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Long.parseLong(matcher.group(1)));
    }

    private static ThreadLocal<ArrayDeque<Long>> getThreadLocal() throws Exception {
        final Field field = MethodLoggingAspect.class.getDeclaredField("START_STACK");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        final ThreadLocal<ArrayDeque<Long>> threadLocal = (ThreadLocal<ArrayDeque<Long>>) field.get(null);
        return threadLocal;
    }

    private List<String> readMessages() {
        if (logFile == null || !Files.exists(logFile)) {
            return List.of();
        }
        try {
            return Files.readAllLines(logFile).stream()
                    .map(MethodLoggingAspectTest::extractMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String extractMessage(String line) {
        Matcher matcher = MESSAGE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    private static final class TestTarget {
        private TestTarget() {
        }
    }

    private static void waitAtLeastMillis(long millis) {
        long end = System.nanoTime() + Duration.ofMillis(millis).toNanos();
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }
}
