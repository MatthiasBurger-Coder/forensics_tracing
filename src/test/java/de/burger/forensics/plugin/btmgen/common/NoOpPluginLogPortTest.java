package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NoOpPluginLogPortTest {

    @Test
    void ignoresAllMessages() {
        PluginLogPort logger = NoOpPluginLogPort.INSTANCE;

        assertDoesNotThrow(() -> {
            logger.info("info");
            logger.warn("warn");
            logger.error("error");
            logger.debug("debug");
        });
    }
}
