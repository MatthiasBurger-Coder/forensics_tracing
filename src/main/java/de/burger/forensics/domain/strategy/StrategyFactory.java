package de.burger.forensics.domain.strategy;

import de.burger.forensics.domain.model.RuleTemplate;

/** Factory for building ConditionStrategy from raw expression text. */
public interface StrategyFactory {
    default ConditionStrategy from(String rawExpression) {
        return from(rawExpression, null, null);
    }

    ConditionStrategy from(String rawExpression, RuleTemplate template, String returnType);
}
