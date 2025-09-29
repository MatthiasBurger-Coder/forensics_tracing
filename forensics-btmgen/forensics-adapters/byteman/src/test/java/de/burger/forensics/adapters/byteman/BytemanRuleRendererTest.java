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
}
