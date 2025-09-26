package de.burger.forensics.plugin.util;

import java.util.Objects;

/** Utility that generates stable identifiers for helper-based rule evaluation. */
public final class RuleIdUtil {
    private RuleIdUtil() {
    }

    public static String stableRuleId(String className, String methodName, int lineNumber, String expression) {
        int hash = Objects.hash(className, methodName, lineNumber, expression);
        return Integer.toHexString(hash);
    }
}
