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
}