package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.Rule;

/**
 * Port for rendering domain rules into concrete output.
 */
public interface RuleRenderPort {
    String render(Rule rule);
}
