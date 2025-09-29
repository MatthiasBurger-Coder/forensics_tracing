package de.burger.forensics.domain.strategy;

/** Factory for building ConditionStrategy from raw expression text. */
public interface StrategyFactory {
    ConditionStrategy from(String rawExpression);
}
