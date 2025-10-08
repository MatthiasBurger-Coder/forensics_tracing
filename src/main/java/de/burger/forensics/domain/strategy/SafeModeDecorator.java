package de.burger.forensics.domain.strategy;

import java.util.Objects;

/**
 * Wraps a ConditionStrategy to evaluate within a SafeEval helper call when safe-mode is enabled.
 * The helper class must be available on the Byteman helper path.
 */
public final class SafeModeDecorator implements ConditionStrategy {

    private final ConditionStrategy delegate;
    private final String helperFqcn;   // e.g., "org.example.trace.SafeEval"
    private final String ruleId;       // stable identifier for this rule/condition

    public SafeModeDecorator(ConditionStrategy delegate, String helperFqcn, String ruleId) {
        this.delegate = Objects.requireNonNull(delegate);
        this.helperFqcn = Objects.requireNonNull(helperFqcn);
        this.ruleId = Objects.requireNonNull(ruleId);
    }

    @Override
    public String toBytemanIf() {
        // Calls SafeEval.eval(ruleId, originalExpressionAsString, delegateExpressionBoolean)
        // The Byteman condition becomes: SafeEval.eval("ruleId","expr", (original))
        final String inner = delegate.toBytemanIf();
        if (inner == null || inner.isBlank()) {
            return inner;
        }
        final String escaped = inner.replace("\\", "\\\\").replace("\"", "\\\"");
        return helperFqcn + ".eval(\"" + ruleId + "\",\"" + escaped + "\"," + "(" + inner + "))";
    }
}
