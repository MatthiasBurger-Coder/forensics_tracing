package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IfRuleStrategyTest {

    @Test
    void wrapsIfTrueConditionWithSafeEval() {
        RuleParams params = new RuleParams(
                "rule-1",
                "com.acme.SwitchingOrderApi",
                "sumGross",
                "(Ljava/lang/String;)D",
                "SwitchingOrderApi#sumGross",
                "$this.policy.newEnabled() && $this.policy.routePredicate().test($1)",
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new IfTrueRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF eval(\"rule-1\", \"$this.policy.newEnabled() && $this.policy.routePredicate().test($1)\", () -> $this.policy.newEnabled() && $this.policy.routePredicate().test($1))")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void wrapsIfFalseConditionWithSafeEvalEvenWhenBlank() {
        RuleParams params = new RuleParams(
                "rule-2",
                "com.acme.SwitchingOrderApi",
                "sumGross",
                "(Ljava/lang/String;)D",
                "SwitchingOrderApi#sumGross",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF eval(\"rule-2\", \"false\", () -> false)")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void usesOriginalConditionStringForFalseBranchEval() {
        RuleParams params = new RuleParams(
                "rule-3",
                "com.acme.SwitchingOrderApi",
                "sumGross",
                "(Ljava/lang/String;)D",
                "SwitchingOrderApi#sumGross",
                "$1 != null && $1 > 0",
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF eval(\"rule-3\", \"!($1 != null && $1 > 0)\", () -> !($1 != null && $1 > 0))")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void stripsEnableLogPlaceholderFromTrueBranch() {
        RuleParams params = new RuleParams(
                "rule-4",
                "com.acme.SwitchingOrderApi",
                "sumGross",
                "(Ljava/lang/String;)D",
                "SwitchingOrderApi#sumGross",
                "ENABLE_LOG",
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new IfTrueRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF eval(\"rule-4\", \"true\", () -> true)")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void stripsEnableLogPlaceholderFromFalseBranch() {
        RuleParams params = new RuleParams(
                "rule-5",
                "com.acme.SwitchingOrderApi",
                "sumGross",
                "(Ljava/lang/String;)D",
                "SwitchingOrderApi#sumGross",
                "!(ENABLE_LOG)",
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF eval(\"rule-5\", \"false\", () -> false)")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void qualifiesBareStaticSingletonFieldsToAvoidParserErrors() {
        RuleParams params = new RuleParams(
                "rule-6",
                "com.acme.legacy.OrderRepository",
                "getInstance",
                "()Lcom/acme/legacy/OrderRepository;",
                "OrderRepository#getInstance",
                "INSTANCE == null",
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF eval(\"rule-6\", \"!(com.acme.legacy.OrderRepository.INSTANCE == null)\", () -> !(com.acme.legacy.OrderRepository.INSTANCE == null))")
                .doesNotContain(" IF eval(\"rule-6\", \"!(INSTANCE == null)\"");
    }
}
