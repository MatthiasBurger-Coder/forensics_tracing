package de.burger.forensics.plugin.strategy;

/** Factory for selecting condition rendering strategies. */
public interface StrategyFactory {
    /** Return a suitable strategy; default to OriginalExpressionStrategy for unknown forms. */
    ConditionStrategy from(String conditionText);
}
