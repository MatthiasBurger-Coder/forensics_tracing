package de.burger.forensics.plugin.adapters;

import de.burger.forensics.domain.port.out.LogPort;
import org.gradle.api.logging.Logger;

/**
 * Bridges the domain logging abstraction to Gradle's logger.
 */
public final class GradleLogAdapter implements LogPort {

    private final Logger logger;

    public GradleLogAdapter(Logger logger) {
        this.logger = logger;
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
    public void debug(String message) {
        logger.debug(message);
    }
}
