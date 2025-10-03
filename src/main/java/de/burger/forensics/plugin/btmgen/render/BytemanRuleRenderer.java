package de.burger.forensics.plugin.btmgen.render;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.plugin.btmgen.render.api.*;
import de.burger.forensics.plugin.btmgen.render.impl.*;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;

import java.util.Objects;

public final class BytemanRuleRenderer {

    private final StrategyRegistry registry;

    public BytemanRuleRenderer(StrategyRegistry registry) {
        this.registry = registry;
    }

    /** Default renderer with built-in strategies. */
    public static BytemanRuleRenderer defaultRenderer() {
        return new BytemanRuleRenderer(
                StrategyRegistry.builder()
                        .register(new ReturnRuleStrategy())
                        .register(new ThrowRuleStrategy())
                        .register(new MethodEnterRuleStrategy())
                        .register(new MethodExitRuleStrategy())
                        .register(new IfTrueRuleStrategy())
                        .register(new IfFalseRuleStrategy())
                        .register(new SwitchRuleStrategy())
                        .register(new SwitchCaseRuleStrategy())
                        .register(new ThreadLifecycleRuleStrategy())
                        .register(new JdbcExecuteRuleStrategy())
                        .build()
        );
    }

    /** For tests/extensions. */
    public static BytemanRuleRenderer of(StrategyRegistry registry) {
        return new BytemanRuleRenderer(Objects.requireNonNull(registry));
    }

    /** Convenience: render by enum template. */
    public String render(RuleTemplate template, RuleParams params) {
        return render(template.name(), params);
    }

    /** Render by template id (extensible). */
    public String render(String templateId, RuleParams params) {
        var strategy = registry.find(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No strategy for template: " + templateId));
        return strategy.render(params);
    }
}
