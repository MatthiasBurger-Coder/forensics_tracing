package de.burger.forensics.plugin.btmgen.render.api;

/** Strategy to render one Byteman rule (or a rule bundle) for a template. */
public interface RuleRenderStrategy {
    /** Unique template id; usually matches RuleTemplate.name(). */
    String id();
    /** Render the Byteman rule text. */
    String render(RuleParams params);
}

