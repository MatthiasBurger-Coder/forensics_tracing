package de.burger.forensics.plugin.strategy;

import java.util.Objects;

/** Decorates a ConditionStrategy and routes unsafe expressions through a helper hook. */
public final class SafeModeDecorator implements ConditionStrategy {
    private final ConditionStrategy delegate;
    private final boolean safeMode;
    private final boolean forceHelperForWhitelist;
    private final String helperFqcn;
    private final String ruleId;

    public SafeModeDecorator(ConditionStrategy delegate, boolean safeMode, boolean forceHelperForWhitelist, String helperFqcn, String ruleId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.safeMode = safeMode;
        this.forceHelperForWhitelist = forceHelperForWhitelist;
        this.helperFqcn = Objects.requireNonNull(helperFqcn, "helperFqcn");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
    }

    @Override
    public String toBytemanIf() {
        if (!safeMode) {
            return delegate.toBytemanIf();
        }
        if (isInlineSafe(delegate) && !forceHelperForWhitelist) {
            return delegate.toBytemanIf();
        }
        return delegate.toHelperIf(helperFqcn, ruleId);
    }

    private static boolean isInlineSafe(ConditionStrategy strategy) {
        return strategy instanceof EqualsLiteralStrategy
            || strategy instanceof InstanceOfStrategy
            || strategy instanceof BooleanCompositeStrategy;
    }
}
