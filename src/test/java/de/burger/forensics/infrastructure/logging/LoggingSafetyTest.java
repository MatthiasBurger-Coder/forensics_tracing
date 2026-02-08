package de.burger.forensics.infrastructure.logging;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingSafetyTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("forensics.btmgen.logToFile");
    }

    @Test
    void doesNotRequireLog4j2AtRuntime() throws Exception {
        System.setProperty("forensics.btmgen.logToFile", "false");
        try (InputStream in = MethodLoggingAspect.class.getResourceAsStream("MethodLoggingAspect.class")) {
            assertThat(in).as("MethodLoggingAspect bytecode").isNotNull();
            byte[] bytes = in.readAllBytes();
            String constants = new String(bytes, StandardCharsets.ISO_8859_1);
            assertThat(constants).doesNotContain("org/apache/logging/log4j/LogManager");
            assertThat(constants).doesNotContain("org/apache/logging/log4j");
        }
    }

    @Test
    void instrumentationMustNotThrow() {
        System.setProperty("forensics.btmgen.logToFile", "false");
        MethodLoggingAspect aspect = new MethodLoggingAspect();
        JoinPoint jp = joinPoint(new ThrowingToString());

        assertDoesNotThrow(() -> aspect.onEnter(jp));
        assertDoesNotThrow(() -> aspect.onReturn(jp));
        assertDoesNotThrow(() -> aspect.onThrow(jp, new RuntimeException("boom")));
    }

    private static JoinPoint joinPoint(Object arg) {
        JoinPoint jp = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(jp.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(LoggingSafetyTest.class);
        when(signature.toShortString()).thenReturn("LoggingSafetyTest.method(..)");
        when(jp.getArgs()).thenReturn(new Object[]{arg});
        return jp;
    }

    private static final class ThrowingToString {
        @Override
        public String toString() {
            throw new RuntimeException("boom");
        }
    }
}
