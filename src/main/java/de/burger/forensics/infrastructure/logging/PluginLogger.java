package de.burger.forensics.infrastructure.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight logger facade to keep the plugin backend-agnostic.
 */
public final class PluginLogger {

    private PluginLogger() {
    }

    public static Logger getLogger(Class<?> type) {
        return LoggerFactory.getLogger(type);
    }
}
