package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.SourceLocation;

import java.util.Objects;

/**
 * Describes a condition expression that may reference a source-level type name Byteman cannot resolve.
 */
public record ConditionValidationIssue(
        SourceLocation location,
        String expression,
        String symbol
) {
    public ConditionValidationIssue {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(symbol, "symbol");
    }

    public String message() {
        return "Suspicious unresolved type reference '" + symbol + "' in condition at "
                + location.fqcn() + "#" + location.method() + ":" + location.line()
                + " -> " + expression;
    }
}
