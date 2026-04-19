package de.burger.forensics.plugin.btmgen.render;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BytemanRuleRendererTest {

    @Test
    void rendersUsingTemplateIdLookup() {
        RecordingStrategy strategy = new RecordingStrategy("CUSTOM", "rendered-custom");
        BytemanRuleRenderer renderer = BytemanRuleRenderer.of(StrategyRegistry.builder().register(strategy).build());
        RuleParams params = params();

        String rendered = renderer.render("CUSTOM", params);

        assertThat(rendered).isEqualTo("rendered-custom");
        assertThat(strategy.lastParams).isSameAs(params);
    }

    @Test
    void rendersUsingRuleTemplateEnumLookup() {
        RecordingStrategy strategy = new RecordingStrategy("RETURN", "rendered-return");
        BytemanRuleRenderer renderer = BytemanRuleRenderer.of(StrategyRegistry.builder().register(strategy).build());

        assertThat(renderer.render(RuleTemplate.RETURN, params())).isEqualTo("rendered-return");
    }

    @Test
    void rejectsUnknownTemplateIds() {
        BytemanRuleRenderer renderer = BytemanRuleRenderer.of(StrategyRegistry.builder().build());

        assertThatThrownBy(() -> renderer.render("UNKNOWN", params()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No strategy for template: UNKNOWN");
    }

    @Test
    void defaultRendererIncludesBuiltInStrategies() {
        BytemanRuleRenderer renderer = BytemanRuleRenderer.defaultRenderer();

        String rendered = renderer.render("METHOD_ENTER", params());

        assertThat(rendered)
                .contains("RULE rule-1")
                .contains("CLASS com.example.Foo")
                .contains("METHOD work()V")
                .contains("AT ENTRY");
    }

    private static RuleParams params() {
        return new RuleParams(
                "rule-1",
                "com.example.Foo",
                "work",
                "()V",
                "Foo#work",
                null,
                null,
                RuleParams.DEFAULT_HELPER_FQN
        );
    }

    private static final class RecordingStrategy implements RuleRenderStrategy {
        private final String id;
        private final String rendered;
        private RuleParams lastParams;

        private RecordingStrategy(String id, String rendered) {
            this.id = id;
            this.rendered = rendered;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String render(RuleParams params) {
            this.lastParams = params;
            return rendered;
        }
    }
}
