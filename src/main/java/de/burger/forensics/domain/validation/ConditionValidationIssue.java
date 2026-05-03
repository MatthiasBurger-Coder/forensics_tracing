package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.SourceLocation;

import java.util.Objects;

/**
 * Describes a condition expression that may reference a source-level type name Byteman cannot resolve.
 */
public record ConditionValidationIssue(
        SourceLocation location,
        String expression,
        String symbol,
        RuleTemplate template
) {
    private static final int PREVIEW_LIMIT = 240;

    public ConditionValidationIssue(SourceLocation location, String expression, String symbol) {
        this(location, expression, symbol, null);
    }

    public ConditionValidationIssue {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(symbol, "symbol");
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
