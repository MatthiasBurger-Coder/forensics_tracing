package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnRuleStrategyTest {

    @Test
    void rendersBooleanReturnCaptureWithoutFilteringFalseValues() {
        RuleParams params = params("$!", "boolean");

        String rule = new ReturnRuleStrategy().render(params);

        assertThat(rule)
                .contains("RULE return-1 : return com.example.Foo#isEnabled")
                .contains("AT EXIT")
                .contains("IF true")
                .contains("onExit(com.example.Foo.class, \"isEnabled\", $! );")
                .doesNotContain("com.example.Foo#isEnabled#isEnabled")
                .doesNotContain("IF $!");
    }

    @Test
    void rendersBooleanLiteralReturnCaptureWithoutFilteringFalseValues() {
        RuleParams params = params("false", "boolean");

        String rule = new ReturnRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF true")
                .contains("onExit(com.example.Foo.class, \"isEnabled\", $! );")
                .doesNotContain("IF false");
    }

    @Test
    void rendersNonBooleanReturnCaptureWithoutConditionFilter() {
        RuleParams params = params("$result", "java.lang.String");

        String rule = new ReturnRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF true")
                .contains("onExit(com.example.Foo.class, \"isEnabled\", $! );")
                .doesNotContain("IF $result");
    }

    @Test
    void rendersVoidReturnWithoutReturnValuePlaceholder() {
        RuleParams params = params("true", "void");

        String rule = new ReturnRuleStrategy().render(params);

        assertThat(rule)
                .contains("IF true")
                .contains("onExit(com.example.Foo.class, \"isEnabled\", null );")
                .doesNotContain("$!");
    }

    private static RuleParams params(String condition, String returnType) {
        return new RuleParams(
                "return-1",
                "com.example.Foo",
                "isEnabled",
                "()Z",
                "com.example.Foo#isEnabled",
                condition,
                null,
                RuleParams.DEFAULT_HELPER_FQN,
                19,
                returnType
        );
    }
}
