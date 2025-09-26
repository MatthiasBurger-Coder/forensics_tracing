package de.burger.forensics.plugin.strategy;

/** Strategy to render a Byteman IF expression without changing semantics. */
public interface ConditionStrategy {
    /** Render the Byteman IF expression. Must not introduce side effects. */
    String toBytemanIf();

    /** Optional: short type tag for debugging/tests. */
    default String kind() {
        return getClass().getSimpleName();
    }

    /** Render the IF expression using helper calls to {@code helperFqcn}. */
    default String toHelperIf(String helperFqcn, String ruleId) {
        return helperFqcn + ".ifMatch(\"" + ruleId + "\")";
    }
}
