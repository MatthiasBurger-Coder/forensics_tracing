package de.burger.forensics.application.service;

import de.burger.forensics.domain.validation.ConditionValidationReport;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Raised when strict condition validation rejects generated rule expressions.
 */
public final class ConditionValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ConditionValidationReport report;

    public ConditionValidationException(ConditionValidationReport report) {
        super(message(report));
        this.report = Objects.requireNonNull(report, "report");
    }

    public ConditionValidationReport report() {
        return report;
    }

    private static String message(ConditionValidationReport report) {
        Objects.requireNonNull(report, "report");
        String details = report.issues().stream()
                .limit(3)
                .map(issue -> issue.symbol() + " at " + issue.location().fqcn() + "#"
                        + issue.location().method() + ":" + issue.location().line())
                .collect(Collectors.joining("; "));
        return "Condition validation failed with " + report.issueCount()
                + " unresolved type reference warning(s)"
                + (details.isBlank() ? "." : ": " + details);
    }
}
