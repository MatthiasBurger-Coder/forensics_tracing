package de.burger.forensics.domain.port.out;

/**
 * Minimal logging abstraction for the domain/application layer.
 */
public interface LogPort {
    void info(String message);
    void warn(String message);
    void debug(String message);
}
