package de.burger.forensics.infrastructure.rt;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;
import org.slf4j.MDC;

/**
 * Lightweight Byteman helper delegating to {@link RtTrace}.
 */
public class RtTraceHelper extends Helper {

    public RtTraceHelper(Rule rule) {
        super(rule);
    }

    public void onEnter(Class<?> clazz, String method, Object... args) {
        try {
            RtTrace.onEnter(clazz, method, args);
        } catch (Exception t) {
            swallow("Failed to trace method entry.", t);
        }
    }

    public void onExit(Class<?> clazz, String method, Object result) {
        try {
            RtTrace.onExit(clazz, method, result);
        } catch (Exception t) {
            swallow("Failed to trace method exit.", t);
        }
    }

    public void onBranch(Class<?> clazz, String method, String branchLabel) {
        try {
            RtTrace.onBranch(clazz, method, branchLabel);
        } catch (Exception t) {
            swallow("Failed to trace branch.", t);
        }
    }

    public void onSwitch(Class<?> clazz, String method, String displayName) {
        try {
            RtTrace.onSwitch(clazz, method, displayName);
        } catch (Exception t) {
            swallow("Failed to trace switch.", t);
        }
    }

    public void onCase(Class<?> clazz, String method, String label) {
        try {
            RtTrace.onCase(clazz, method, label);
        } catch (Exception t) {
            swallow("Failed to trace case.", t);
        }
    }

    public void onException(Throwable throwable) {
        try {
            RtTrace.onException(throwable);
        } catch (Exception t) {
            swallow("Failed to trace exception.", t);
        }
    }

    public void ioBegin(String op, String target) {
        try {
            RtTrace.ioBegin(op, target);
        } catch (Exception t) {
            swallow("Failed to trace io begin.", t);
        }
    }

    public void ioEnd(String op, String target) {
        try {
            RtTrace.ioEnd(op, target);
        } catch (Exception t) {
            swallow("Failed to trace io end.", t);
        }
    }

    public void threadFork(String threadName) {
        try {
            RtTrace.threadFork(threadName);
        } catch (Exception t) {
            swallow("Failed to trace thread fork.", t);
        }
    }

    public void threadJoin(String threadName) {
        try {
            RtTrace.threadJoin(threadName);
        } catch (Exception t) {
            swallow("Failed to trace thread join.", t);
        }
    }

    public boolean eval(String ruleId, String expression, BooleanSupplier supplier) {
        try {
            String label = Objects.toString(ruleId, "") + ":" + Objects.toString(expression, "");
            boolean value = supplier.getAsBoolean();
            RtTrace.branch(label, value);
            return value;
        } catch (Exception t) {
            try {
                RtTrace.conditionError(ruleId, expression, t);
            } catch (Exception ignored) {
                // ignore
            }
            return false;
        }
    }

    public boolean eval(String ruleId, String expression, boolean value) {
        return eval(ruleId, expression, () -> value);
    }

    /**
     * Returns the current value stored in MDC for the given key.
     */
    public String mdc(String key) {
        return MDC.get(key);
    }

    /**
     * Convenience method for a standard correlation id key.
     */
    public String correlationId() {
        return MDC.get("correlationId");
    }

    private void swallow(String message, Throwable t) {
        try {
            de.burger.forensics.infrastructure.logging.PluginLogger
                    .getLogger(RtTraceHelper.class)
                    .debug(message, t);
        } catch (Exception ignored) {
            // never fail application due to logging failures
        }
    }
}
