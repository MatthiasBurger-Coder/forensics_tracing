package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.common.PluginLogPort;
import org.gradle.api.logging.Logger;

import java.util.Objects;

/**
 * Bridges common BTM generation logging to Gradle task logging.
 */
final class GradlePluginLogAdapter implements PluginLogPort {

    private final Logger logger;

    GradlePluginLogAdapter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void info(String message) {
        logger.lifecycle(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void debug(String message) {
        logger.debug(message);
    }
}
