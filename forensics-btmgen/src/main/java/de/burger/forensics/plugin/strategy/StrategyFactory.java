package de.burger.forensics.plugin.strategy;

/** Factory for building ConditionStrategy from raw expression text. */
public interface StrategyFactory {
    ConditionStrategy from(String rawExpression);
}
