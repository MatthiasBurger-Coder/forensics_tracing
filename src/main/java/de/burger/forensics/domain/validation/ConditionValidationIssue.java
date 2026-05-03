package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.ConditionResolutionStatus;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.SourceContext;

import java.util.Objects;

/**
 * Describes a condition expression that may reference a source-level type name Byteman cannot resolve.
 */
public record ConditionValidationIssue(
        SourceLocation location,
        String expression,
        String symbol,
        RuleTemplate template,
        ConditionResolutionStatus resolutionStatus,
        String reason,
        SourceContext sourceContext
) {
    private static final int PREVIEW_LIMIT = 240;

    public ConditionValidationIssue(SourceLocation location, String expression, String symbol) {
        this(location, expression, symbol, null);
    }

    public ConditionValidationIssue(SourceLocation location,
                                    String expression,
                                    String symbol,
                                    RuleTemplate template) {
        this(
                location,
                expression,
                symbol,
                template,
                ConditionResolutionStatus.UNRESOLVED,
                "",
                SourceContext.empty());
    }

    public ConditionValidationIssue {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(resolutionStatus, "resolutionStatus");
        reason = Objects.requireNonNullElse(reason, "");
        sourceContext = Objects.requireNonNullElse(sourceContext, SourceContext.empty());
    }

    public String message() {
        return "Suspicious unresolved type reference '" + symbol + "' in condition at "
                + location.fqcn() + "#" + location.method() + ":" + location.line()
                + " -> " + expressionPreview();
    }

    public String expressionPreview() {
        String oneLine = expression.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (oneLine.length() <= PREVIEW_LIMIT) {
            return oneLine;
        }
        return oneLine.substring(0, PREVIEW_LIMIT - 3) + "...";
    }
}
