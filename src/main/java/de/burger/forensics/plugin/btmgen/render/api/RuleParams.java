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
        String helperFqn,    // helper implementation invoked from the rule
        int sourceLine,      // optional source line, <= 0 if unavailable
        String returnType    // optional Java return type, null if unavailable
) {
    public static final String DEFAULT_HELPER_FQN = "de.burger.forensics.infrastructure.rt.RtTraceHelper";
    public static final int UNKNOWN_SOURCE_LINE = -1;

    public RuleParams {
        if (helperFqn == null || helperFqn.isBlank()) {
            helperFqn = DEFAULT_HELPER_FQN;
        }
    }

    public RuleParams(String id,
                      String className,
                      String methodName,
                      String methodDesc,
                      String displayName,
                      String condition,
                      String sqlHint,
                      String helperFqn) {
        this(id, className, methodName, methodDesc, displayName, condition, sqlHint, helperFqn, UNKNOWN_SOURCE_LINE, null);
    }

}
