package de.burger.forensics.plugin.strategy;

/**
 * Renders a source-level condition expression into a Byteman-compatible IF() fragment.
 * Implementations should not add surrounding "IF" or trailing rule lines; just the condition body.
 */
public interface ConditionStrategy {

    /**
     * @return Byteman IF() condition expression that is safe to embed, without surrounding "IF" keyword.
     */
    String toBytemanIf();

    /**
     * Optional: for debugging/diagnostics.
     */
    default String typeName() {
        return getClass().getSimpleName();
    }
}
