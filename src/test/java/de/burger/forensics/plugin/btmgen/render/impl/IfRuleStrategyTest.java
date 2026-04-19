package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IfRuleStrategyTest {

    @Test
    void rendersIfTrueAtSourceLineInsteadOfEntry() {
        RuleParams params = params(
                "rule-1",
                "$this.policy.newEnabled() && $this.policy.routePredicate().test($1)",
                27
        );

        String rule = new IfTrueRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 27")
                .doesNotContain("AT ENTRY")
                .contains("IF eval(\"rule-1\", \"$this.policy.newEnabled() && $this.policy.routePredicate().test($1)\", $this.policy.newEnabled() && $this.policy.routePredicate().test($1))")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void rendersIfFalseAtSourceLineInsteadOfEntry() {
        RuleParams params = params("rule-2", null, 41);

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 41")
                .doesNotContain("AT ENTRY")
                .contains("IF eval(\"rule-2\", \"false\", false)")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void preservesSafeEvalExpressionAfterLineRenderingChange() {
        RuleParams params = params("rule-3", "$1 != null && $1 > 0", 56);

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 56")
                .doesNotContain("AT ENTRY")
                .contains("IF eval(\"rule-3\", \"!($1 != null && $1 > 0)\", !($1 != null && $1 > 0))")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void stripsEnableLogPlaceholderFromTrueBranch() {
        RuleParams params = params("rule-4", "ENABLE_LOG", 60);

        String rule = new IfTrueRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 60")
                .contains("IF eval(\"rule-4\", \"true\", true)")
                .doesNotContain("ENABLE_LOG");
    }

    @Test
    void stripsEnableLogPlaceholderFromFalseBranch() {
        RuleParams params = params("rule-5", "!(ENABLE_LOG)", 64);

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 64")
                .contains("IF eval(\"rule-5\", \"false\", false)")
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
                RuleParams.DEFAULT_HELPER_FQN,
                72
        );

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 72")
                .contains("IF eval(\"rule-6\", \"!(com.acme.legacy.OrderRepository.INSTANCE == null)\", !(com.acme.legacy.OrderRepository.INSTANCE == null))")
                .doesNotContain(" IF eval(\"rule-6\", \"!(INSTANCE == null)\"");
    }

    @Test
    void resolvesClassPlaceholderForStaticFieldConditions() {
        RuleParams params = new RuleParams(
                "rule-7",
                "com.acme.legacy.OrderRepository",
                "getInstance",
                "()Lcom/acme/legacy/OrderRepository;",
                "OrderRepository#getInstance",
                "$CLASS.INSTANCE == null",
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                79
        );

        String rule = new IfFalseRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 79")
                .contains("IF eval(\"rule-7\", \"!(com.acme.legacy.OrderRepository.INSTANCE == null)\", !(com.acme.legacy.OrderRepository.INSTANCE == null))")
                .doesNotContain("$CLASS.INSTANCE");
    }

    @Test
    void failsExplicitlyWhenSourceLineIsMissing() {
        RuleParams ifTrueParams = params("rule-8", "$1 > 0");
        RuleParams ifFalseParams = params("rule-9", "$1 <= 0");

        assertThatThrownBy(() -> new IfTrueRuleStrategy().render(ifTrueParams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IF_TRUE rule requires a valid source line");
        assertThatThrownBy(() -> new IfFalseRuleStrategy().render(ifFalseParams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IF_FALSE rule requires a valid source line");
    }

    private static RuleParams params(String id, String condition) {
        return new RuleParams(
                id,
                "com.acme.SwitchingOrderApi",
                "sumGross",
                "(Ljava/lang/String;)D",
                "SwitchingOrderApi#sumGross",
                condition,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );
    }

    private static RuleParams params(String id, String condition, int sourceLine) {
        return new RuleParams(
                id,
                "com.acme.SwitchingOrderApi",
                "sumGross",
                "(Ljava/lang/String;)D",
                "SwitchingOrderApi#sumGross",
                condition,
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                sourceLine
        );
    }
}
