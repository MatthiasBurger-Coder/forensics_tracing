package de.burger.forensics.domain.strategy;

import de.burger.forensics.domain.model.RuleId;

/**
 * Utility for wrapping strategies when safe mode is enabled.
 */
public final class SafeMode {
    private SafeMode() {
    }

    public static ConditionStrategy wrap(ConditionStrategy base,
                                         String helperFqcn,
                                         RuleId ruleId) {
        return new SafeModeDecorator(base, helperFqcn, ruleId.value());
    }
}
