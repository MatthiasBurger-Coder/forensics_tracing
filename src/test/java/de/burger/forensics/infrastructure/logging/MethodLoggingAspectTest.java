package de.burger.forensics.infrastructure.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodLoggingAspectTest {

    private static final Pattern OK_PATTERN = Pattern.compile("OK in (\\d+) ms");

    private LoggerContext loggerContext;
    private Configuration loggerConfig;
    private ListAppender listAppender;
    private String loggerName;

    @BeforeEach
    void setUpLogging() {
        loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerConfig = loggerContext.getConfiguration();
        loggerName = TestTarget.class.getName();

        listAppender = new ListAppender("list-" + UUID.randomUUID());
        listAppender.start();
        loggerConfig.addAppender(listAppender);

        final LoggerConfig rootConfig = loggerConfig.getRootLogger();
        rootConfig.addAppender(listAppender, Level.WARN, null);
        rootConfig.setLevel(Level.WARN);

        final LoggerConfig config = new LoggerConfig(loggerName, Level.WARN, false);
        config.addAppender(listAppender, Level.WARN, null);
        loggerConfig.addLogger(loggerName, config);
        loggerContext.updateLoggers();
    }

    @AfterEach
    void tearDownLogging() {
        loggerConfig.getRootLogger().removeAppender(listAppender.getName());
        loggerConfig.removeLogger(loggerName);
        listAppender.stop();
        loggerContext.updateLoggers();
    }

    @Test
    void doesNotReportUptimeLikeDurationsForNestedCalls() throws Exception {
        final MethodLoggingAspect aspect = new MethodLoggingAspect();
        final JoinPoint outer = joinPoint("TestTarget.outer(..)");
        final JoinPoint inner = joinPoint("TestTarget.inner(..)");

        aspect.onEnter(outer);
        Thread.sleep(10);
        aspect.onEnter(inner);
        Thread.sleep(5);
        aspect.onReturn(inner);
        Thread.sleep(5);
        aspect.onReturn(outer);

        final List<Long> durations = listAppender.getEvents().stream()
                .map(event -> event.getMessage().getFormattedMessage())
                .map(MethodLoggingAspectTest::extractDurationMs)
                .flatMap(java.util.Optional::stream)
                .toList();

        assertThat(durations).hasSize(2);
        assertThat(durations).allMatch(value -> value >= 0 && value < Duration.ofMinutes(1).toMillis());
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

        assertThat(listAppender.getEvents())
                .anyMatch(event -> event.getMessage().getFormattedMessage().contains("(unbalanced timing stack)"));
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

    private static final class TestTarget {
        private TestTarget() {
        }
    }
}
