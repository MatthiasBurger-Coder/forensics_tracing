package de.burger.forensics.adapters.byteman;

import static org.assertj.core.api.Assertions.assertThat;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.RuleId;
import de.burger.forensics.domain.model.RuleType;
import de.burger.forensics.domain.model.SourceLocation;
import org.junit.jupiter.api.Test;

class BytemanRuleRendererTest {

    private final BytemanRuleRenderer renderer = new BytemanRuleRenderer();

    @Test
    void rendersIfRule() {
        Rule rule = new Rule(
            new RuleId("abc123"),
            new SourceLocation("com.example.Demo", "test", 12),
            "value > 0",
            true,
            "org.example.trace.SafeEval",
            RuleType.IF_TRUE
        );

        String rendered = renderer.render(rule);
        assertThat(rendered)
            .contains("RULE abc123")
            .contains("CLASS com.example.Demo")
            .contains("IF (value > 0)");
    }

    @Test
    void rendersIfFalseRuleWithNegatedCondition() {
        Rule rule = new Rule(
            new RuleId("xyz"),
            new SourceLocation("com.example.Demo", "test", 10),
            "value > 0",
            false,
            "helper",
            RuleType.IF_FALSE
        );

        String rendered = renderer.render(rule);
        assertThat(rendered).contains("IF (!(value > 0))");
    }

    @Test
    void rendersSwitchAndCaseRules() {
        SourceLocation location = new SourceLocation("com.example.Demo", "test", 22);
        Rule switchRule = new Rule(new RuleId("sw"), location, "selector", true, "helper", RuleType.SWITCH);
        Rule caseRule = new Rule(new RuleId("case"), location, "value\n\"x\"", true, "helper", RuleType.SWITCH_CASE);

        String switchRendered = renderer.render(switchRule);
        String caseRendered = renderer.render(caseRule);

        assertThat(switchRendered).contains("DO sw(");
        assertThat(caseRendered).contains("kase(").contains("\\n").contains("\\\"");
    }

    @Test
    void rendersReturnAndThrowRules() {
        SourceLocation location = new SourceLocation("com.example.Demo", "test", 30);
        Rule returnRule = new Rule(new RuleId("ret"), location, "return 1", true, "helper", RuleType.RETURN);
        Rule throwRule = new Rule(new RuleId("thr"), location, "new Ex()", true, "helper", RuleType.THROW);

        assertThat(renderer.render(returnRule)).contains("DO ret(");
        assertThat(renderer.render(throwRule)).contains("DO thr(").contains("new Ex()");
    }

    @Test
    void rendersEntryAndExitRules() {
        SourceLocation location = new SourceLocation("com.example.Demo", "test", 1);
        Rule entryRule = new Rule(new RuleId("en"), location, "true", true, "helper", RuleType.ENTRY);
        Rule exitRule = new Rule(new RuleId("ex"), location, "true", true, "helper", RuleType.EXIT);

        assertThat(renderer.render(entryRule)).contains("AT ENTRY").contains("enter(");
        assertThat(renderer.render(exitRule)).contains("AT EXIT").contains("exit(");
    }
}
