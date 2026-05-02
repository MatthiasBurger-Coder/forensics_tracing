package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BtmGenerationExceptionTest {

    @Test
    void keepsMessage() {
        BtmGenerationException exception = new BtmGenerationException("failed");

        assertEquals("failed", exception.getMessage());
    }

    @Test
    void keepsCause() {
        IllegalStateException cause = new IllegalStateException("cause");
        BtmGenerationException exception = new BtmGenerationException("failed", cause);

        assertEquals("failed", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
