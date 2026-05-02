package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.PluginLogPort;
import org.apache.maven.plugin.logging.Log;

import java.util.Objects;

/**
 * Bridges common BTM generation logging to Maven plugin logging.
 */
final class MavenLogAdapter implements PluginLogPort {

    private final Log log;

    MavenLogAdapter(Log log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void warn(String message) {
        log.warn(message);
    }

    @Override
    public void error(String message) {
        log.error(message);
    }

    @Override
    public void debug(String message) {
        log.debug(message);
    }
}
