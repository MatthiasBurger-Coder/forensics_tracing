package de.burger.forensics.domain.model;

import java.util.Objects;

/**
 * Structured diagnostic for a condition expression captured during source scanning.
 */
public record ConditionDiagnostic(String symbol,
                                  String expressionPreview,
                                  ConditionResolutionStatus resolutionStatus,
                                  String reason,
                                  SourceLocation location,
                                  SourceContext sourceContext) {

    public ConditionDiagnostic {
        Objects.requireNonNull(symbol, "symbol");
        expressionPreview = Objects.requireNonNullElse(expressionPreview, "");
        Objects.requireNonNull(resolutionStatus, "resolutionStatus");
        reason = Objects.requireNonNullElse(reason, "");
        Objects.requireNonNull(location, "location");
        sourceContext = Objects.requireNonNullElse(sourceContext, SourceContext.empty());
    }
}
