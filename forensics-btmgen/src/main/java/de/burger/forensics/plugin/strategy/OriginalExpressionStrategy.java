package de.burger.forensics.plugin.strategy;

/** Pass-through for original expression to preserve current behavior. */
public final class OriginalExpressionStrategy implements ConditionStrategy {
    private final String raw;

    public OriginalExpressionStrategy(String raw) {
        this.raw = raw;
    }

    @Override
    public String toBytemanIf() {
        return raw;
    }
}
