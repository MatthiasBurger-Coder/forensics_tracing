package de.burger.forensics.application.tracing;

/**
 * Minimal tracing facade for the application layer.
 * Keeps the domain free from any technical tracing concerns.
 */
public interface Tracer {
    void enter(Class<?> clazz, String method, Object... args);
    void exit(Class<?> clazz, String method, Object result);
    AutoCloseable span(String name);
    void branch(String label, Object value);
    void setVariable(String name, Object value);
    void error(Throwable t);
    void setCorrelationId(String correlationId);
    String newCorrelationId();
}
