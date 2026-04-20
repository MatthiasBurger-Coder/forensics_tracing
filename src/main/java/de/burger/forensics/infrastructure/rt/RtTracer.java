package de.burger.forensics.infrastructure.rt;

import de.burger.forensics.application.tracing.Tracer;

/**
 * Tracer implementation based on RtTrace. Inject this into application services.
 */
public final class RtTracer implements Tracer {

    @Override public void enter(Class<?> c, String m, Object... a) { RtTrace.onEnter(c, m, a); }

    @Override public void exit(Class<?> c, String m, Object r) { RtTrace.onExit(c, m, r); }

    @Override public AutoCloseable span(String name) {
        var tok = RtTrace.beginSpan(name);
        return () -> RtTrace.endSpan(tok);
    }

    @Override public void branch(String label, Object value) { RtTrace.branch(label, value); }

    @Override public void setVariable(String name, Object value) { RtTrace.varSet(name, value); }

    @Override public void error(Throwable t) { RtTrace.onException(t); }

    @Override public void setCorrelationId(String correlationId) { RtTrace.setCorrelationId(correlationId); }

    @Override public String newCorrelationId() { return RtTrace.newCorrelationId(); }
}
