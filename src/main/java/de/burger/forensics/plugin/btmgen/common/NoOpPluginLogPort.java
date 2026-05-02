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
        // Intentionally ignores messages when no build-tool logger is available.
    }

    @Override
    public void warn(String message) {
        // Intentionally ignores messages when no build-tool logger is available.
    }

    @Override
    public void error(String message) {
        // Intentionally ignores messages when no build-tool logger is available.
    }

    @Override
    public void debug(String message) {
        // Intentionally ignores messages when no build-tool logger is available.
    }
}
