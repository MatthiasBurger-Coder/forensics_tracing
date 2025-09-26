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

    @Override
    public String toHelperIf(String helperFqcn, String ruleId) {
        if (children.isEmpty()) {
            return "true";
        }
        String acc = children.get(0).toHelperIf(helperFqcn, ruleId);
        for (int i = 1; i < children.size(); i++) {
            String rhs = children.get(i).toHelperIf(helperFqcn, ruleId);
            String fun = op == Op.AND ? "and" : "or";
            acc = helperFqcn + "." + fun + "(" + acc + ", " + rhs + ")";
        }
        return acc;
    }
}
