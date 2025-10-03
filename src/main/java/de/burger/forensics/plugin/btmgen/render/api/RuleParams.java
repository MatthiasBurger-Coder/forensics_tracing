package de.burger.forensics.plugin.btmgen.render.api;

/** Neutral parameter set passed to rule strategies. Extend if needed. */
public record RuleParams(
        String id,           // rule id or id prefix; strategies may derive sub-ids
        String className,    // target type (for METHOD_ENTER/RETURN/THROW)
        String methodName,   // target method name
        String methodDesc,   // optional descriptor/signature, may be null
        String displayName,  // human label (optional)
        String condition,    // optional IF guard (Byteman expression)
        String sqlHint,      // optional SQL preview for JDBC template
        String helperFqn     // helper implementation invoked from the rule
) {
    public static final String DEFAULT_HELPER_FQN = "de.burger.forensics.infrastructure.rt.RtTrace";

    public RuleParams {
        if (helperFqn == null || helperFqn.isBlank()) {
            helperFqn = DEFAULT_HELPER_FQN;
        }
    }
}
