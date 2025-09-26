package de.burger.forensics.plugin.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleIdUtilTest {
    @Test
    void producesStableIdForSameInput() {
        String first = RuleIdUtil.stableRuleId("C", "m", 42, "expr");
        String second = RuleIdUtil.stableRuleId("C", "m", 42, "expr");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void producesDifferentIdsForDifferentInput() {
        String first = RuleIdUtil.stableRuleId("C", "m", 42, "expr");
        String second = RuleIdUtil.stableRuleId("C", "m", 43, "expr");
        assertThat(first).isNotEqualTo(second);
    }
}
