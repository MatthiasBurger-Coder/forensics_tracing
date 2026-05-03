package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwitchAndJdbcRuleStrategyTest {

    @Test
    void rendersSwitchRuleAtSourceLineWithEmptySelectorWhenConditionIsMissing() {
        RuleParams params = new RuleParams(
                "switch-1",
                "com.example.Foo",
                "work",
                "()V",
                null,
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                31,
                null
        );

        String rule = new SwitchRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE switch-1 : switch com.example.Foo#work")
                .contains("AT LINE 31")
                .doesNotContain("AT ENTRY")
                .contains("onSwitch(com.example.Foo.class, \"work\", \"\" );");
    }

    @Test
    void rendersSwitchRuleWithTargetTitleAndEscapedSelectorMetadata() {
        RuleParams params = new RuleParams(
                "switch-2",
                "com.example.Foo",
                "work",
                "()V",
                "com.example.Foo#work",
                "case \"1\"",
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                33,
                null
        );

        String rule = new SwitchRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE switch-2 : switch com.example.Foo#work")
                .doesNotContain("case \"1\"#work")
                .contains("AT LINE 33")
                .doesNotContain("AT ENTRY")
                .contains("onSwitch(com.example.Foo.class, \"work\", \"case \\\"1\\\"\" );");
    }

    @Test
    void rendersSwitchRuleAtSourceLineWithEscapedSelectorMetadata() {
        RuleParams params = new RuleParams(
                "switch-3",
                "com.example.Foo",
                "work",
                "()V",
                "com.example.Foo#work",
                "$1.kind(\"fast\")",
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                37,
                null
        );

        String rule = new SwitchRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 37")
                .doesNotContain("AT ENTRY")
                .contains("onSwitch(com.example.Foo.class, \"work\", \"$1.kind(\\\"fast\\\")\" );");
    }

    @Test
    void rendersSwitchCaseRuleAtSourceLineWithFallbackLabelWhenCaseLabelIsMissing() {
        RuleParams params = new RuleParams(
                "case-1",
                "com.example.Foo",
                "work",
                "()V",
                null,
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                43,
                null
        );

        String rule = new SwitchCaseRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE case-1 : switch-case com.example.Foo#work")
                .contains("AT LINE 43")
                .doesNotContain("AT ENTRY")
                .contains("onCase(com.example.Foo.class, \"work\", \"<case>\");");
    }

    @Test
    void rendersSwitchCaseRuleWithTargetTitleAndFallbackLabel() {
        RuleParams params = new RuleParams(
                "case-2",
                "com.example.Foo",
                "work",
                "()V",
                "com.example.Foo#work",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                45,
                null
        );

        String rule = new SwitchCaseRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE case-2 : switch-case com.example.Foo#work")
                .doesNotContain("label \"quoted\"#work")
                .contains("AT LINE 45")
                .doesNotContain("AT ENTRY")
                .contains("onCase(com.example.Foo.class, \"work\", \"<case>\");");
    }

    @Test
    void rendersSwitchCaseRuleAtSourceLineWithCaseLabelOnlyInAction() {
        RuleParams params = new RuleParams(
                "case-3",
                "com.example.Foo",
                "work",
                "()V",
                "com.example.Foo#work",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                41,
                null,
                "case \"quoted\""
        );

        String rule = new SwitchCaseRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT LINE 41")
                .doesNotContain("AT ENTRY")
                .contains("RULE case-3 : switch-case com.example.Foo#work")
                .doesNotContain("RULE case-3 : switch-case case \"quoted\"#work")
                .contains("onCase(com.example.Foo.class, \"work\", \"case \\\"quoted\\\"\");")
                .doesNotContain("onCase(com.example.Foo.class, \"work\", \"case \\\"quoted\\\"#work\");");
    }

    @Test
    void rejectsSwitchRuleWithoutSourceLine() {
        RuleParams params = new RuleParams(
                "switch-missing-line",
                "com.example.Foo",
                "work",
                "()V",
                "com.example.Foo#work",
                "$1",
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        assertThatThrownBy(() -> new SwitchRuleStrategy().render(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SWITCH rule requires a valid source line for com.example.Foo#work");
    }

    @Test
    void rejectsSwitchCaseRuleWithoutSourceLine() {
        RuleParams params = new RuleParams(
                "case-missing-line",
                "com.example.Foo",
                "work",
                "()V",
                "com.example.Foo#work",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        assertThatThrownBy(() -> new SwitchCaseRuleStrategy().render(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SWITCH_CASE rule requires a valid source line for com.example.Foo#work");
    }

    @Test
    void rendersJdbcRuleWithoutHintWhenSqlHintIsNull() {
        RuleParams params = paramsWithSqlHint(null);

        String rule = new JdbcExecuteRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE jdbc-1-begin : jdbc io begin")
                .contains("RULE jdbc-1-end : jdbc io end")
                .doesNotContain(" :: ");
    }

    @Test
    void rendersJdbcRuleWithoutHintWhenSqlHintIsBlank() {
        String rule = new JdbcExecuteRuleStrategy().render(paramsWithSqlHint("   "));

        assertThat(rule).doesNotContain(" :: ");
    }

    @Test
    void rendersJdbcRuleWithShortSqlHint() {
        String rule = new JdbcExecuteRuleStrategy().render(paramsWithSqlHint("SELECT \"id\" FROM orders"));

        assertThat(rule)
                .contains(" :: SELECT \\\"id\\\" FROM orders")
                .doesNotContain("...");
    }

    @Test
    void rendersJdbcRuleWithTrimmedSqlHint() {
        String hint = "123456789012345678901234567890123456789012345678901234567890123456789012345678901";
        String expectedTrimmed = "12345678901234567890123456789012345678901234567890123456789012345678901234567...";

        String rule = new JdbcExecuteRuleStrategy().render(paramsWithSqlHint(hint));

        assertThat(rule)
                .contains(" :: " + expectedTrimmed)
                .doesNotContain(" :: " + hint);
    }

    private static RuleParams paramsWithSqlHint(String sqlHint) {
        return new RuleParams(
                "jdbc-1",
                "com.example.Foo",
                "work",
                "()V",
                null,
                null,
                sqlHint,
                RuleParams.DEFAULT_HELPER_FQN
        );
    }
}
