package de.burger.forensics.plugin.btmgen.common;

/**
 * Logger implementation for callers that do not need generation logs.
 */
public final class NoOpPluginLogPort implements PluginLogPort {

    public static final NoOpPluginLogPort INSTANCE = new NoOpPluginLogPort();

    private NoOpPluginLogPort() {
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void error(String message) {
    }

    @Override
    public void debug(String message) {
    }
}
