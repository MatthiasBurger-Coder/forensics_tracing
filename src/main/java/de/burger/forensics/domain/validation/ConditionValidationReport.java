package de.burger.forensics.domain.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Immutable report for non-fatal condition validation findings.
 */
public record ConditionValidationReport(List<ConditionValidationIssue> issues) {

    public ConditionValidationReport {
        LinkedHashMap<String, ConditionValidationIssue> unique = new LinkedHashMap<>();
        Objects.requireNonNull(issues, "issues").forEach(issue -> unique.putIfAbsent(issueKey(issue), issue));
        issues = List.copyOf(unique.values());
    }

    public static ConditionValidationReport empty() {
        return new ConditionValidationReport(List.of());
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public int issueCount() {
        return issues.size();
    }

    public ConditionValidationReport merge(ConditionValidationReport other) {
        Objects.requireNonNull(other, "other");
        LinkedHashMap<String, ConditionValidationIssue> merged = new LinkedHashMap<>();
        issues.forEach(issue -> merged.put(issueKey(issue), issue));
        other.issues().forEach(issue -> merged.putIfAbsent(issueKey(issue), issue));
        return new ConditionValidationReport(new ArrayList<>(merged.values()));
    }

    private static String issueKey(ConditionValidationIssue issue) {
        return issue.location() + "|" + issue.expression() + "|" + issue.symbol();
    }
}
