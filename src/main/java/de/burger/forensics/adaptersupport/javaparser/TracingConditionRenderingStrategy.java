package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decorator for {@link ConditionRenderingStrategy} that collects rendered fragments for debugging.
 */
public record TracingConditionRenderingStrategy(ConditionRenderingStrategy delegate,
                                                List<String> traces) implements ConditionRenderingStrategy {

    public TracingConditionRenderingStrategy(ConditionRenderingStrategy delegate) {
        this(delegate, new ArrayList<>());
    }

    public TracingConditionRenderingStrategy(ConditionRenderingStrategy delegate, List<String> traces) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.traces = Objects.requireNonNull(traces, "traces");
    }

    @Override
    public String renderCondition(Expression condition, MethodScanContext context) {
        String rendered = delegate.renderCondition(condition, context);
        appendTrace("condition", rendered);
        return rendered;
    }

    @Override
    public String renderReturn(ReturnStmt returnStmt, MethodScanContext context) {
        String rendered = delegate.renderReturn(returnStmt, context);
        appendTrace("return", rendered);
        return rendered;
    }

    @Override
    public String renderSwitchLabel(SwitchEntry entry, MethodScanContext context) {
        String rendered = delegate.renderSwitchLabel(entry, context);
        appendTrace("switch", rendered);
        return rendered;
    }

    void appendTrace(String kind, String renderedValue) {
        traces.add(kind + ":" + renderedValue);
    }
}
