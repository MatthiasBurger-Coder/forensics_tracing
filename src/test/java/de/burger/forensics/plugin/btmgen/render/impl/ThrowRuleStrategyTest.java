package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThrowRuleStrategyTest {

    @Test
    void throwRuleShouldUseBooleanConditionInsteadOfThrowExpression() {
        RuleParams params = new RuleParams(
                "throw-1",
                "com.example.Service",
                "map",
                null,
                "com.example.Service#map",
                "SomeLogger.LOGGER.someExceptionFactoryCall($this.value)",
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );

        String rule = new ThrowRuleStrategy().render(params);

        assertThat(rule)
                .contains("AT THROW")
                .contains("IF true")
                .contains("onException($^);");
        assertThat(ifLine(rule)).isEqualTo("IF true");
    }

    private static String ifLine(String rule) {
        return rule.lines()
                .filter(line -> line.stripLeading().startsWith("IF "))
                .findFirst()
                .map(String::strip)
                .orElseThrow();
    }
}
