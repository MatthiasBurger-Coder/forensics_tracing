package de.burger.forensics.plugin.strategy;

import java.util.List;

/** Renders composite boolean expressions with explicit parentheses. */
public final class BooleanCompositeStrategy implements ConditionStrategy {
    public enum Op { AND, OR }

    private final Op op;
    private final List<ConditionStrategy> children;

    public BooleanCompositeStrategy(Op op, List<ConditionStrategy> children) {
        this.op = op;
        this.children = children;
    }

    @Override
    public String toBytemanIf() {
        return children.stream()
            .map(ConditionStrategy::toBytemanIf)
            .reduce((a, b) -> "(" + a + ") " + op.name() + " (" + b + ")")
            .orElse("true");
    }
}
