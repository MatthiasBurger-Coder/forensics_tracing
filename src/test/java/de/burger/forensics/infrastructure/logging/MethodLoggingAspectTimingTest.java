package de.burger.forensics.infrastructure.logging;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodLoggingAspectTimingTest {

    private static final Pattern OK_IN_PATTERN = Pattern.compile("OK in (\\d+) ms");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^.*\\] \\[cid=.*?\\] (.*)$");

    private final MethodLoggingAspect aspect = new MethodLoggingAspect();
    private Path logFile;

    @BeforeEach
    void setUp() throws Exception {
        logFile = Files.createTempFile("forensics-btmgen", "-" + UUID.randomUUID() + ".log");
        System.setProperty("forensics.btmgen.logToFile", "true");
        System.setProperty("forensics.btmgen.logFile", logFile.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("forensics.btmgen.logToFile");
        System.clearProperty("forensics.btmgen.logFile");
        if (logFile != null) {
            Files.deleteIfExists(logFile);
        }
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
        if (logFile == null || !Files.exists(logFile)) {
            return List.of();
        }
        try {
            return Files.readAllLines(logFile).stream()
                    .map(MethodLoggingAspectTimingTest::extractMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Long> extractElapsedMs() {
        return messages().stream()
                .map(OK_IN_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> Long.parseLong(matcher.group(1)))
                .toList();
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
}
