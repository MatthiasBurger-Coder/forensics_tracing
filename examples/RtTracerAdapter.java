package examples;

import de.burger.forensics.application.tracing.Tracer;
import de.burger.forensics.infrastructure.rt.RtSpanToken;
import de.burger.forensics.infrastructure.rt.RtTrace;

/**
 * Minimal adapter that bridges the application-facing {@link Tracer} interface
 * to the runtime {@link RtTrace} helper. Drop this class into your project to
 * keep instrumentation code free from direct RtTrace calls.
 */
public final class RtTracerAdapter implements Tracer {

    @Override
    public void enter(Class<?> clazz, String method, Object... args) {
        RtTrace.onEnter(clazz, method, args);
    }

    @Override
    public void exit(Class<?> clazz, String method, Object result) {
        RtTrace.onExit(clazz, method, result);
    }

    @Override
    public AutoCloseable span(String name) {
        RtSpanToken token = RtTrace.beginSpan(name);
        return () -> RtTrace.endSpan(token);
    }

    @Override
    public void branch(String label, Object value) {
        RtTrace.branch(label, value);
    }

    @Override
    public void setVariable(String name, Object value) {
        RtTrace.varSet(name, value);
    }

    @Override
    public void error(Throwable t) {
        RtTrace.onException(t);
    }

    @Override
    public void setCorrelationId(String correlationId) {
        RtTrace.setCorrelationId(correlationId);
    }

    @Override
    public String newCorrelationId() {
        return RtTrace.newCorrelationId();
    }
}
