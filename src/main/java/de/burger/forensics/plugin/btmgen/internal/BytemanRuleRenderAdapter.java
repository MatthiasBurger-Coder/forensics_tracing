package de.burger.forensics.plugin.btmgen.internal;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.port.out.RuleRenderPort;
import de.burger.forensics.plugin.btmgen.render.BytemanRuleRenderer;
import de.burger.forensics.plugin.btmgen.render.api.RuleParams;

import java.util.Objects;

/**
 * Bridges the domain rule model to the Byteman renderer used inside the Gradle task.
 */
public final class BytemanRuleRenderAdapter implements RuleRenderPort {

    private final BytemanRuleRenderer renderer;

    public BytemanRuleRenderAdapter(BytemanRuleRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public String render(Rule rule) {
        Objects.requireNonNull(rule, "rule");
        RuleTemplate template = Objects.requireNonNull(rule.type(), "rule.type");
        var location = Objects.requireNonNull(rule.location(), "rule.location");

        String className = defaultIfBlank(location.fqcn(), "<unknown>");
        String methodName = defaultIfBlank(location.method(), "<method>");
        String displayName = className + "#" + methodName;
        String condition = rule.condition();

        if (template == RuleTemplate.SWITCH_CASE && rule.condition() != null && !rule.condition().isBlank()) {
            displayName = rule.condition();
            condition = null;
        }

        RuleParams params = new RuleParams(
                Objects.requireNonNull(rule.id(), "rule.id").value(),
                className,
                methodName,
                null,
                displayName,
                condition,
                null,
                rule.helperFqn(),
                location.line()
        );
        return renderer.render(template.name(), params);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
