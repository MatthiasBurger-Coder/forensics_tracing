package de.burger.forensics.plugin.btmgen.internal;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.RuleId;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.plugin.btmgen.render.BytemanRuleRenderer;
import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BytemanRuleRenderAdapterTest {

    @Test
    void propagatesLocationConditionAndSourceLineToRuleParams() {
        CapturingStrategy strategy = new CapturingStrategy("IF_TRUE");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-1"),
                new SourceLocation("com.example.Foo", "work", 19),
                "flag",
                true,
                "com.example.Helper",
                RuleTemplate.IF_TRUE
        );

        String rendered = adapter.render(rule);

        assertThat(rendered).isEqualTo("rendered:IF_TRUE");
        assertThat(strategy.lastParams.className()).isEqualTo("com.example.Foo");
        assertThat(strategy.lastParams.methodName()).isEqualTo("work");
        assertThat(strategy.lastParams.displayName()).isEqualTo("com.example.Foo#work");
        assertThat(strategy.lastParams.condition()).isEqualTo("flag");
        assertThat(strategy.lastParams.helperFqn()).isEqualTo("com.example.Helper");
        assertThat(strategy.lastParams.sourceLine()).isEqualTo(19);
    }

    @Test
    void propagatesMethodSignatureToRuleParams() {
        CapturingStrategy strategy = new CapturingStrategy("METHOD_ENTER");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-signature"),
                new SourceLocation("com.example.Foo", "work", 19),
                "true",
                true,
                "com.example.Helper",
                RuleTemplate.METHOD_ENTER,
                "work(String, int)"
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.methodName()).isEqualTo("work");
        assertThat(strategy.lastParams.methodDesc()).isEqualTo("(String, int)");
    }

    @Test
    void leavesMethodDescriptionUnsetWhenRuleHasNoSignature() {
        CapturingStrategy strategy = new CapturingStrategy("METHOD_ENTER");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-no-signature"),
                new SourceLocation("com.example.Foo", "work", 19),
                "true",
                true,
                "com.example.Helper",
                RuleTemplate.METHOD_ENTER
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.methodDesc()).isNull();
    }

    @Test
    void leavesMethodDescriptionUnsetWhenRuleSignatureIsBlank() {
        CapturingStrategy strategy = new CapturingStrategy("METHOD_ENTER");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-blank-signature"),
                new SourceLocation("com.example.Foo", "work", 19),
                "true",
                true,
                "com.example.Helper",
                RuleTemplate.METHOD_ENTER,
                "   "
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.methodDesc()).isNull();
    }

    @Test
    void acceptsMethodSignatureThatAlreadyContainsOnlyParameters() {
        CapturingStrategy strategy = new CapturingStrategy("METHOD_ENTER");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-parameter-signature"),
                new SourceLocation("com.example.Foo", "work", 19),
                "true",
                true,
                "com.example.Helper",
                RuleTemplate.METHOD_ENTER,
                "(String, int)"
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.methodDesc()).isEqualTo("(String, int)");
    }

    @Test
    void usesParameterSuffixWhenSignatureMethodNameDiffers() {
        CapturingStrategy strategy = new CapturingStrategy("METHOD_ENTER");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-different-name"),
                new SourceLocation("com.example.Foo", "work", 19),
                "true",
                true,
                "com.example.Helper",
                RuleTemplate.METHOD_ENTER,
                "execute(String, int)"
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.methodDesc()).isEqualTo("(String, int)");
    }

    @Test
    void ignoresInvalidMethodSignatureWithoutParameters() {
        CapturingStrategy strategy = new CapturingStrategy("METHOD_ENTER");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-invalid-signature"),
                new SourceLocation("com.example.Foo", "work", 19),
                "true",
                true,
                "com.example.Helper",
                RuleTemplate.METHOD_ENTER,
                "execute"
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.methodDesc()).isNull();
    }

    @Test
    void mapsSwitchCaseConditionToDisplayNameAndClearsCondition() {
        CapturingStrategy strategy = new CapturingStrategy("SWITCH_CASE");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-2"),
                new SourceLocation("com.example.Foo", "work", 27),
                "case 1",
                true,
                RuleParams.DEFAULT_HELPER_FQN,
                RuleTemplate.SWITCH_CASE
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.displayName()).isEqualTo("case 1");
        assertThat(strategy.lastParams.condition()).isNull();
        assertThat(strategy.lastParams.sourceLine()).isEqualTo(27);
    }

    @Test
    void fallsBackToUnknownNamesWhenLocationValuesAreBlank() {
        CapturingStrategy strategy = new CapturingStrategy("RETURN");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-3"),
                new SourceLocation(" ", "", 5),
                "result",
                true,
                RuleParams.DEFAULT_HELPER_FQN,
                RuleTemplate.RETURN
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.className()).isEqualTo("<unknown>");
        assertThat(strategy.lastParams.methodName()).isEqualTo("<method>");
        assertThat(strategy.lastParams.displayName()).isEqualTo("<unknown>#<method>");
    }

    @Test
    void keepsBlankSwitchCaseConditionWhenItCannotBecomeALabel() {
        CapturingStrategy strategy = new CapturingStrategy("SWITCH_CASE");
        BytemanRuleRenderAdapter adapter = new BytemanRuleRenderAdapter(rendererWith(strategy));
        Rule rule = new Rule(
                new RuleId("rule-4"),
                new SourceLocation(null, null, 8),
                "   ",
                true,
                RuleParams.DEFAULT_HELPER_FQN,
                RuleTemplate.SWITCH_CASE
        );

        adapter.render(rule);

        assertThat(strategy.lastParams.className()).isEqualTo("<unknown>");
        assertThat(strategy.lastParams.methodName()).isEqualTo("<method>");
        assertThat(strategy.lastParams.displayName()).isEqualTo("<unknown>#<method>");
        assertThat(strategy.lastParams.condition()).isEqualTo("   ");
    }

    private static BytemanRuleRenderer rendererWith(CapturingStrategy strategy) {
        return BytemanRuleRenderer.of(StrategyRegistry.builder().register(strategy).build());
    }

    private static final class CapturingStrategy implements RuleRenderStrategy {
        private final String id;
        private RuleParams lastParams;

        private CapturingStrategy(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String render(RuleParams params) {
            this.lastParams = params;
            return "rendered:" + id;
        }
    }
}
