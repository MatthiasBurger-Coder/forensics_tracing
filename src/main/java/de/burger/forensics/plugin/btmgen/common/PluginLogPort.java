package de.burger.forensics.plugin.btmgen.common;

/**
 * Build-tool-neutral logging port for common plugin generation code.
 */
public interface PluginLogPort {

    void info(String message);

    void warn(String message);

    void error(String message);

    void debug(String message);
}
