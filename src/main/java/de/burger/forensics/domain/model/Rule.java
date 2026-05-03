package de.burger.forensics.domain.model;

/**
 * Domain representation of a Byteman rule.
 */
public record Rule(RuleId id,
                   SourceLocation location,
                   String condition,
                   boolean positive,
                   String helperFqn,
                   RuleTemplate type,
                   String methodSignature) {
    public Rule(RuleId id,
                SourceLocation location,
                String condition,
                boolean positive,
                String helperFqn,
                RuleTemplate type) {
        this(id, location, condition, positive, helperFqn, type, null);
    }
}
