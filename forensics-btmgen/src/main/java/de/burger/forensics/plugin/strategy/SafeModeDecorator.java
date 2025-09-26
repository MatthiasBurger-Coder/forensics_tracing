package de.burger.forensics.plugin.strategy;

import java.util.Objects;

/** Decorates a ConditionStrategy and routes unsafe expressions through a helper hook. */
public final class SafeModeDecorator implements ConditionStrategy {
    private final ConditionStrategy delegate;
    private final boolean safeMode;
    private final String helperFqcn;
    private final String ruleId;

    public SafeModeDecorator(ConditionStrategy delegate, boolean safeMode, String helperFqcn, String ruleId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.safeMode = safeMode;
        this.helperFqcn = Objects.requireNonNull(helperFqcn, "helperFqcn");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
    }

    @Override
    public String toBytemanIf() {
        if (!safeMode || isInlineSafe(delegate)) {
            return delegate.toBytemanIf();
        }
        return helperFqcn + ".ifMatch(\"" + ruleId + "\")";
    }

    private static boolean isInlineSafe(ConditionStrategy strategy) {
        return strategy instanceof EqualsLiteralStrategy
            || strategy instanceof InstanceOfStrategy
            || strategy instanceof BooleanCompositeStrategy;
    }
}
