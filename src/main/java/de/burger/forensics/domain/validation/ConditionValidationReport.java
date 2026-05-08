package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.SourceContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable report for non-fatal condition validation findings.
 */
public record ConditionValidationReport(List<ConditionValidationIssue> issues,
                                        List<ConditionValidationIssue> suppressedIssues) {

    private static final String ISSUES_FIELD = "issues";

    public ConditionValidationReport(List<ConditionValidationIssue> issues) {
        this(issues, List.of());
    }

    public ConditionValidationReport {
        LinkedHashMap<IssueKey, ConditionValidationIssue> unique = new LinkedHashMap<>();
        Objects.requireNonNull(issues, ISSUES_FIELD).forEach(issue -> unique.putIfAbsent(issueKey(issue), issue));
        issues = List.copyOf(unique.values());
        LinkedHashMap<IssueKey, ConditionValidationIssue> suppressed = new LinkedHashMap<>();
        Objects.requireNonNull(suppressedIssues, "suppressedIssues")
                .forEach(issue -> suppressed.putIfAbsent(issueKey(issue), issue));
        suppressedIssues = List.copyOf(suppressed.values());
    }

    public static ConditionValidationReport empty() {
        return new ConditionValidationReport(List.of(), List.of());
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public int issueCount() {
        return issues.size();
    }

    public int uniqueSymbolCount() {
        return uniqueSymbols().size();
    }

    public boolean hasSuppressedIssues() {
        return !suppressedIssues.isEmpty();
    }

    public int suppressedIssueCount() {
        return suppressedIssues.size();
    }

    public Set<String> uniqueSymbols() {
        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        issues.forEach(issue -> symbols.add(issue.symbol()));
        return Set.copyOf(symbols);
    }

    public List<SymbolGroup> symbolGroups() {
        LinkedHashMap<String, SymbolGroupBuilder> groups = new LinkedHashMap<>();
        issues.forEach(issue -> groups.computeIfAbsent(issue.symbol(), SymbolGroupBuilder::new).add(issue));
        return groups.values().stream()
                .map(SymbolGroupBuilder::build)
                .toList();
    }

    public String summaryMessage(String detailTarget) {
        String details = detailTarget == null || detailTarget.isBlank()
                ? "validation report"
                : detailTarget;
        return "Suspicious unresolved type references: " + issueCount()
                + " occurrences, " + uniqueSymbolCount()
                + " unique names. Details: " + details;
    }

    public ConditionValidationReport merge(ConditionValidationReport other) {
        Objects.requireNonNull(other, "other");
        LinkedHashMap<IssueKey, ConditionValidationIssue> merged = new LinkedHashMap<>();
        issues.forEach(issue -> merged.put(issueKey(issue), issue));
        other.issues().forEach(issue -> merged.putIfAbsent(issueKey(issue), issue));
        LinkedHashMap<IssueKey, ConditionValidationIssue> mergedSuppressed = new LinkedHashMap<>();
        suppressedIssues.forEach(issue -> mergedSuppressed.put(issueKey(issue), issue));
        other.suppressedIssues().forEach(issue -> mergedSuppressed.putIfAbsent(issueKey(issue), issue));
        return new ConditionValidationReport(new ArrayList<>(merged.values()), new ArrayList<>(mergedSuppressed.values()));
    }

    private static IssueKey issueKey(ConditionValidationIssue issue) {
        return new IssueKey(
                stringValue(issue.location().fqcn()),
                stringValue(issue.location().method()),
                issue.location().line(),
                issue.symbol(),
                issue.expression(),
                issue.sourceContext());
    }

    private static String stringValue(String value) {
        return value == null ? "" : value;
    }

    private record IssueKey(String fqcn,
                            String method,
                            int line,
                            String symbol,
                            String expression,
                            SourceContext sourceContext) {
        private IssueKey {
            Objects.requireNonNull(fqcn, "fqcn");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(sourceContext, "sourceContext");
        }
    }

    public record SymbolGroup(String symbol,
                              List<PackageGroup> packages,
                              List<ConditionValidationIssue> issues) {
        public SymbolGroup {
            Objects.requireNonNull(symbol, "symbol");
            packages = List.copyOf(Objects.requireNonNull(packages, "packages"));
            issues = List.copyOf(Objects.requireNonNull(issues, ISSUES_FIELD));
        }

        public int occurrenceCount() {
            return issues.size();
        }

        public int packageCount() {
            return packages.size();
        }

        public int classCount() {
            return packages.stream()
                    .mapToInt(PackageGroup::classCount)
                    .sum();
        }

        public int methodCount() {
            return packages.stream()
                    .mapToInt(PackageGroup::methodCount)
                    .sum();
        }
    }

    public record PackageGroup(String packageName, List<ClassGroup> classes) {
        public PackageGroup {
            Objects.requireNonNull(packageName, "packageName");
            classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        }

        public int classCount() {
            return classes.size();
        }

        public int methodCount() {
            return classes.stream()
                    .mapToInt(ClassGroup::methodCount)
                    .sum();
        }
    }

    public record ClassGroup(String className, List<MethodGroup> methods) {
        public ClassGroup {
            Objects.requireNonNull(className, "className");
            methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
        }

        public int methodCount() {
            return methods.size();
        }
    }

    public record MethodGroup(String methodName, List<ConditionValidationIssue> issues) {
        public MethodGroup {
            Objects.requireNonNull(methodName, "methodName");
            issues = List.copyOf(Objects.requireNonNull(issues, ISSUES_FIELD));
        }

        public int occurrenceCount() {
            return issues.size();
        }
    }

    private static final class SymbolGroupBuilder {
        private final String symbol;
        private final LinkedHashMap<String, PackageGroupBuilder> packages = new LinkedHashMap<>();
        private final List<ConditionValidationIssue> issues = new ArrayList<>();

        private SymbolGroupBuilder(String symbol) {
            this.symbol = symbol;
        }

        private void add(ConditionValidationIssue issue) {
            issues.add(issue);
            packages.computeIfAbsent(packageName(issue), PackageGroupBuilder::new).add(issue);
        }

        private SymbolGroup build() {
            return new SymbolGroup(
                    symbol,
                    packages.values().stream()
                            .map(PackageGroupBuilder::build)
                            .toList(),
                    issues);
        }

        private static String packageName(ConditionValidationIssue issue) {
            String sourcePackage = issue.sourceContext().packageName();
            if (!sourcePackage.isBlank()) {
                return sourcePackage;
            }
            String fqcn = stringValue(issue.location().fqcn());
            int lastDot = fqcn.lastIndexOf('.');
            return lastDot < 0 ? "" : fqcn.substring(0, lastDot);
        }
    }

    private static final class PackageGroupBuilder {
        private final String packageName;
        private final LinkedHashMap<String, ClassGroupBuilder> classes = new LinkedHashMap<>();

        private PackageGroupBuilder(String packageName) {
            this.packageName = packageName;
        }

        private void add(ConditionValidationIssue issue) {
            classes.computeIfAbsent(className(issue), ClassGroupBuilder::new).add(issue);
        }

        private PackageGroup build() {
            return new PackageGroup(
                    packageName,
                    classes.values().stream()
                            .map(ClassGroupBuilder::build)
                            .toList());
        }

        private static String className(ConditionValidationIssue issue) {
            String sourceClass = issue.sourceContext().simpleClassName();
            if (!sourceClass.isBlank()) {
                return sourceClass;
            }
            String fqcn = stringValue(issue.location().fqcn());
            int lastDot = fqcn.lastIndexOf('.');
            return lastDot < 0 ? fqcn : fqcn.substring(lastDot + 1);
        }
    }

    private static final class ClassGroupBuilder {
        private final String className;
        private final LinkedHashMap<String, MethodGroupBuilder> methods = new LinkedHashMap<>();

        private ClassGroupBuilder(String className) {
            this.className = className;
        }

        private void add(ConditionValidationIssue issue) {
            methods.computeIfAbsent(methodName(issue), MethodGroupBuilder::new).add(issue);
        }

        private ClassGroup build() {
            return new ClassGroup(
                    className,
                    methods.values().stream()
                            .map(MethodGroupBuilder::build)
                            .toList());
        }

        private static String methodName(ConditionValidationIssue issue) {
            String sourceMethod = issue.sourceContext().methodName();
            if (!sourceMethod.isBlank()) {
                return sourceMethod;
            }
            return stringValue(issue.location().method());
        }
    }

    private static final class MethodGroupBuilder {
        private final String methodName;
        private final List<ConditionValidationIssue> issues = new ArrayList<>();

        private MethodGroupBuilder(String methodName) {
            this.methodName = methodName;
        }

        private void add(ConditionValidationIssue issue) {
            issues.add(issue);
        }

        private MethodGroup build() {
            return new MethodGroup(methodName, issues);
        }
    }
}
