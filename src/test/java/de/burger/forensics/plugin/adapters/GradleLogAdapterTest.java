package de.burger.forensics.plugin.adapters;

import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

class GradleLogAdapterTest {

    /**
     * Tests for the info method of GradleLogAdapter.
     * <p>
     * The info method is expected to delegate the provided message
     * to the Logger's lifecycle method.
     */

    @Test
    void testInfo_logsMessageWithLifecycleLevel() {
        // Arrange
        Logger loggerMock = Mockito.mock(Logger.class);
        GradleLogAdapter gradleLogAdapter = new GradleLogAdapter(loggerMock);
        String testMessage = "This is an info message";

        // Act
        gradleLogAdapter.info(testMessage);

        // Assert
        verify(loggerMock, times(1)).lifecycle(testMessage);
        verifyNoMoreInteractions(loggerMock);
    }

    @Test
    void testWarn_logsMessageWithWarnLevel() {
        Logger loggerMock = Mockito.mock(Logger.class);
        GradleLogAdapter gradleLogAdapter = new GradleLogAdapter(loggerMock);
        String testMessage = "This is a warn message";

        gradleLogAdapter.warn(testMessage);

        verify(loggerMock, times(1)).warn(testMessage);
        verifyNoMoreInteractions(loggerMock);
    }

    @Test
    void testWarn_handlesNullMessageGracefully() {
        Logger loggerMock = Mockito.mock(Logger.class);
        GradleLogAdapter gradleLogAdapter = new GradleLogAdapter(loggerMock);

        gradleLogAdapter.warn(null);

        verify(loggerMock).warn(null);
        verifyNoMoreInteractions(loggerMock);
    }

    @Test
    void testDebug_logsMessageWithDebugLevel() {
        Logger loggerMock = Mockito.mock(Logger.class);
        GradleLogAdapter gradleLogAdapter = new GradleLogAdapter(loggerMock);
        String testMessage = "This is a debug message";

        gradleLogAdapter.debug(testMessage);

        verify(loggerMock, times(1)).debug(testMessage);
        verifyNoMoreInteractions(loggerMock);
    }
}