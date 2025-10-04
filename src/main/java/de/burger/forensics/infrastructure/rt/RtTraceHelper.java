package de.burger.forensics.infrastructure.rt;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

/**
 * Lightweight Byteman helper delegating to {@link RtTrace}.
 */
public final class RtTraceHelper extends Helper {

    public RtTraceHelper(Rule rule) {
        super(rule);
    }

    public void onEnter(Class<?> clazz, String method, Object... args) {
        RtTrace.onEnter(clazz, method, args);
    }

    public void onExit(Class<?> clazz, String method, Object result) {
        RtTrace.onExit(clazz, method, result);
    }

    public void onBranch(Class<?> clazz, String method, String branchLabel) {
        RtTrace.onBranch(clazz, method, branchLabel);
    }

    public void onSwitch(Class<?> clazz, String method, String displayName) {
        RtTrace.onSwitch(clazz, method, displayName);
    }

    public void onCase(Class<?> clazz, String method, String label) {
        RtTrace.onCase(clazz, method, label);
    }

    public void onException(Throwable throwable) {
        RtTrace.onException(throwable);
    }

    public void ioBegin(String op, String target) {
        RtTrace.ioBegin(op, target);
    }

    public void ioEnd(String op, String target) {
        RtTrace.ioEnd(op, target);
    }

    public void threadFork(String threadName) {
        RtTrace.threadFork(threadName);
    }

    public void threadJoin(String threadName) {
        RtTrace.threadJoin(threadName);
    }
}
