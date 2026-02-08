package de.burger.forensics.infrastructure.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodLoggingAspectTimingTest {

    private static final Pattern OK_IN_PATTERN = Pattern.compile("OK in (\\d+) ms");

    private final MethodLoggingAspect aspect = new MethodLoggingAspect();
    private LoggerContext loggerContext;
    private Configuration loggerConfig;
    private ListAppender listAppender;
    private String loggerName;

    @BeforeEach
    void setUp() {
        System.setProperty("forensics.btmgen.logToFile", "false");
        loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerConfig = loggerContext.getConfiguration();
        loggerName = TestTarget.class.getName();

        listAppender = new ListAppender("list-appender");
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
    void tearDown() {
        if (loggerConfig != null && listAppender != null) {
            loggerConfig.getRootLogger().removeAppender(listAppender.getName());
            if (loggerName != null) {
                loggerConfig.removeLogger(loggerName);
            }
            listAppender.stop();
            if (loggerContext != null) {
                loggerContext.updateLoggers();
            }
        }
        System.clearProperty("forensics.btmgen.logToFile");
    }

    @Test
    void doesNotReportUptimeLikeDurationsForNestedCalls() {
        JoinPoint outer = joinPoint("TestTarget.outer(..)");
        JoinPoint inner = joinPoint("TestTarget.inner(..)");

        aspect.onEnter(outer);
        aspect.onEnter(inner);
        aspect.onReturn(inner);
        aspect.onReturn(outer);

        List<Long> elapsed = extractElapsedMs();
        assertThat(elapsed).hasSize(2);
        assertThat(elapsed).allMatch(value -> value < 60_000L);
    }

    @Test
    void warnsOnUnbalancedReturn() {
        JoinPoint jp = joinPoint("TestTarget.orphan(..)");

        aspect.onReturn(jp);

        List<String> messages = messages();
        assertThat(messages).anyMatch(message -> message.contains("(unbalanced timing stack)"));
    }

    @Test
    void cleansUpThreadLocalWhenStackIsEmpty() {
        JoinPoint jp = joinPoint("TestTarget.single(..)");

        aspect.onEnter(jp);
        aspect.onReturn(jp);
        aspect.onReturn(jp);

        List<String> messages = messages();
        assertThat(messages).anyMatch(message -> message.contains("(unbalanced timing stack)"));
    }

    private JoinPoint joinPoint(String shortSignature) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(TestTarget.class);
        when(signature.toShortString()).thenReturn(shortSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        return joinPoint;
    }

    private List<String> messages() {
        return listAppender.getEvents().stream()
                .map(LogEvent::getMessage)
                .map(message -> message.getFormattedMessage())
                .collect(Collectors.toList());
    }

    private List<Long> extractElapsedMs() {
        return messages().stream()
                .map(OK_IN_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> Long.parseLong(matcher.group(1)))
                .collect(Collectors.toList());
    }

    private static final class TestTarget {
        private TestTarget() {
        }
    }
}
