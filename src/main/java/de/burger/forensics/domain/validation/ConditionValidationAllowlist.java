package de.burger.forensics.domain.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explicit allowlist for known unresolved condition validation findings.
 */
public record ConditionValidationAllowlist(List<Entry> entries) {

    public ConditionValidationAllowlist {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public static ConditionValidationAllowlist empty() {
        return new ConditionValidationAllowlist(List.of());
    }

    public ConditionValidationReport apply(ConditionValidationReport report) {
        Objects.requireNonNull(report, "report");
        List<ConditionValidationIssue> reported = new ArrayList<>();
        List<ConditionValidationIssue> suppressed = new ArrayList<>(report.suppressedIssues());
        report.issues().forEach(issue -> addToTarget(issue, reported, suppressed));
        return new ConditionValidationReport(reported, suppressed);
    }

    private void addToTarget(ConditionValidationIssue issue,
                             List<ConditionValidationIssue> reported,
                             List<ConditionValidationIssue> suppressed) {
        if (suppresses(issue)) {
            suppressed.add(issue);
            return;
        }
        reported.add(issue);
    }

    private boolean suppresses(ConditionValidationIssue issue) {
        return entries.stream().anyMatch(entry -> entry.matches(issue));
    }

    public record Entry(String symbol, String reason, Scope scope, String packageName) {

        public Entry {
            symbol = requiredText(symbol, "symbol");
            reason = requiredText(reason, "reason");
            Objects.requireNonNull(scope, "scope");
            packageName = Objects.requireNonNullElse(packageName, "").trim();
            if (scope == Scope.PACKAGE && packageName.isBlank()) {
                throw new IllegalArgumentException("Allowlist packageName must not be blank for package scope.");
            }
        }

        public static Entry global(String symbol, String reason) {
            return new Entry(symbol, reason, Scope.GLOBAL, "");
        }

        public static Entry packageScoped(String symbol, String packageName, String reason) {
            return new Entry(symbol, reason, Scope.PACKAGE, packageName);
        }

        private boolean matches(ConditionValidationIssue issue) {
            if (!symbol.equals(issue.symbol())) {
                return false;
            }
            return scope == Scope.GLOBAL || packageName.equals(issuePackageName(issue));
        }

        public enum Scope {
            GLOBAL,
            PACKAGE
        }
    }

    private static String requiredText(String value, String name) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Allowlist " + name + " must not be blank.");
        }
        return normalized;
    }

    private static String issuePackageName(ConditionValidationIssue issue) {
        String sourcePackage = issue.sourceContext().packageName();
        if (!sourcePackage.isBlank()) {
            return sourcePackage;
        }
        String fqcn = Objects.requireNonNullElse(issue.location().fqcn(), "");
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot < 0 ? "" : fqcn.substring(0, lastDot);
    }
}
