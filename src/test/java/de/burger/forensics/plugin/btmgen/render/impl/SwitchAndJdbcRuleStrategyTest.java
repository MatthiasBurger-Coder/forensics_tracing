package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchAndJdbcRuleStrategyTest {

    @Test
    void rendersSwitchRuleWithEmptyLabelWhenDisplayNameIsMissing() {
        RuleParams params = new RuleParams(
                "switch-1",
                "com.example.Foo",
                "work",
                "()V",
                null,
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new SwitchRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE switch-1 : switch com.example.Foo#work")
                .contains("onSwitch(com.example.Foo.class, \"work\", \"\" );");
    }

    @Test
    void rendersSwitchRuleWithEscapedDisplayName() {
        RuleParams params = new RuleParams(
                "switch-2",
                "com.example.Foo",
                "work",
                "()V",
                "case \"1\"",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new SwitchRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE switch-2 : switch case \"1\"#work")
                .contains("onSwitch(com.example.Foo.class, \"work\", \"case \\\"1\\\"\" );");
    }

    @Test
    void rendersSwitchCaseRuleWithFallbackLabelWhenDisplayNameIsMissing() {
        RuleParams params = new RuleParams(
                "case-1",
                "com.example.Foo",
                "work",
                "()V",
                null,
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new SwitchCaseRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE case-1 : switch-case com.example.Foo#work")
                .contains("onCase(com.example.Foo.class, \"work\", \"<case>\");");
    }

    @Test
    void rendersSwitchCaseRuleWithEscapedDisplayName() {
        RuleParams params = new RuleParams(
                "case-2",
                "com.example.Foo",
                "work",
                "()V",
                "label \"quoted\"",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new SwitchCaseRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE case-2 : switch-case label \"quoted\"#work")
                .contains("onCase(com.example.Foo.class, \"work\", \"label \\\"quoted\\\"\");");
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
