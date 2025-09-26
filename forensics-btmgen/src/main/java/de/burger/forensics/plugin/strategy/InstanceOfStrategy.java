package de.burger.forensics.plugin.strategy;

/** Renders: expr instanceof fqcn. */
public final class InstanceOfStrategy implements ConditionStrategy {
    private final String expr;
    private final String fqcn;

    public InstanceOfStrategy(String expr, String fqcn) {
        this.expr = expr;
        this.fqcn = fqcn;
    }

    @Override
    public String toBytemanIf() {
        return expr + " instanceof " + fqcn;
    }

    @Override
    public String toHelperIf(String helperFqcn, String ruleId) {
        return helperFqcn + ".ifInstanceOf(" + expr + ", \"" + fqcn + "\")";
    }
}
