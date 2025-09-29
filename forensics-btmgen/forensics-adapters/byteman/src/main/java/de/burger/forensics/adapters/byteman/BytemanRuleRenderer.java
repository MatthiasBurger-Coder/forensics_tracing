package de.burger.forensics.adapters.byteman;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.RuleType;
import de.burger.forensics.domain.port.out.RuleRenderPort;

/**
 * Renders domain rules into Byteman textual representation.
 */
public final class BytemanRuleRenderer implements RuleRenderPort {

    @Override
    public String render(Rule rule) {
        return switch (rule.type()) {
            case IF_TRUE -> renderIf(rule, true);
            case IF_FALSE -> renderIf(rule, false);
            case SWITCH -> renderSwitch(rule);
            case SWITCH_CASE -> renderCase(rule);
            case RETURN -> renderReturn(rule);
            case THROW -> renderThrow(rule);
            case ENTRY -> renderEntry(rule);
            case EXIT -> renderExit(rule);
        };
    }

    private String renderIf(Rule rule, boolean positive) {
        String condition = positive ? rule.condition() : "!(" + rule.condition() + ")";
        return """
            RULE %s
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT LINE %d
            IF (%s)
            DO trace("%s")
            ENDRULE
            """.formatted(
            rule.id().value(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.helperFqn(),
            rule.location().line(),
            condition,
            rule.id().value()
        ).strip();
    }

    private String renderSwitch(Rule rule) {
        return """
            RULE %s
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT LINE %d
            DO sw("%s","%s",%d,"%s")
            ENDRULE
            """.formatted(
            rule.id().value(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.helperFqn(),
            rule.location().line(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.location().line(),
            escape(rule.condition())
        ).strip();
    }

    private String renderCase(Rule rule) {
        return """
            RULE %s
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT LINE %d
            DO kase("%s","%s",%d,"%s")
            ENDRULE
            """.formatted(
            rule.id().value(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.helperFqn(),
            rule.location().line(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.location().line(),
            escape(rule.condition())
        ).strip();
    }

    private String renderReturn(Rule rule) {
        return """
            RULE %s
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT LINE %d
            DO ret("%s","%s",%d)
            ENDRULE
            """.formatted(
            rule.id().value(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.helperFqn(),
            rule.location().line(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.location().line()
        ).strip();
    }

    private String renderThrow(Rule rule) {
        return """
            RULE %s
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT LINE %d
            DO thr("%s","%s",%d,"%s")
            ENDRULE
            """.formatted(
            rule.id().value(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.helperFqn(),
            rule.location().line(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.location().line(),
            escape(rule.condition())
        ).strip();
    }

    private String renderEntry(Rule rule) {
        return """
            RULE %s
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT ENTRY
            DO enter("%s","%s",$LINE)
            ENDRULE
            """.formatted(
            rule.id().value(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.helperFqn(),
            rule.location().fqcn(),
            rule.location().method()
        ).strip();
    }

    private String renderExit(Rule rule) {
        return """
            RULE %s
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT EXIT
            DO exit("%s","%s",$LINE)
            ENDRULE
            """.formatted(
            rule.id().value(),
            rule.location().fqcn(),
            rule.location().method(),
            rule.helperFqn(),
            rule.location().fqcn(),
            rule.location().method()
        ).strip();
    }

    private String escape(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
