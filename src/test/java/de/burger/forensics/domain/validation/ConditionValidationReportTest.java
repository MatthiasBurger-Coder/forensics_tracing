package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.ConditionResolutionStatus;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.SourceContext;
import de.burger.forensics.domain.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionValidationReportTest {

    @Test
    void groupsIssuesBySymbolPackageClassMethodAndKeepsRawFindings() {
        ConditionValidationIssue first = issue(
                "DeploymentType",
                "org.example.deployment",
                "DeploymentProcessor",
                "deploy",
                184);
        ConditionValidationIssue second = issue(
                "DeploymentType",
                "org.example.deployment",
                "DeploymentProcessor",
                "deploy",
                195);
        ConditionValidationIssue third = issue(
                "OtherType",
                "org.example.other",
                "OtherProcessor",
                "run",
                42);
        ConditionValidationReport report = new ConditionValidationReport(List.of(first, second, third));

        assertThat(report.issues()).containsExactly(first, second, third);
        assertThat(report.symbolGroups())
                .extracting(ConditionValidationReport.SymbolGroup::symbol)
                .containsExactly("DeploymentType", "OtherType");

        ConditionValidationReport.SymbolGroup group = report.symbolGroups().get(0);

        assertThat(group.occurrenceCount()).isEqualTo(2);
        assertThat(group.packageCount()).isEqualTo(1);
        assertThat(group.classCount()).isEqualTo(1);
        assertThat(group.methodCount()).isEqualTo(1);
        assertThat(group.packages()).singleElement()
                .satisfies(packageGroup -> {
                    assertThat(packageGroup.packageName()).isEqualTo("org.example.deployment");
                    assertThat(packageGroup.classes()).singleElement()
                            .satisfies(classGroup -> {
                                assertThat(classGroup.className()).isEqualTo("DeploymentProcessor");
                                assertThat(classGroup.methods()).singleElement()
                                        .satisfies(methodGroup -> {
                                            assertThat(methodGroup.methodName()).isEqualTo("deploy");
                                            assertThat(methodGroup.issues()).containsExactly(first, second);
                                        });
                            });
                });
    }

    @Test
    void groupsLegacyIssuesBySourceLocationFallbacks() {
        ConditionValidationIssue issue = new ConditionValidationIssue(
                new SourceLocation("org.example.legacy.LegacyProcessor", "run", 17),
                "LegacyType.enabled()",
                "LegacyType");

        ConditionValidationReport report = new ConditionValidationReport(List.of(issue));

        ConditionValidationReport.SymbolGroup group = report.symbolGroups().get(0);
        assertThat(group.packages()).singleElement()
                .satisfies(packageGroup -> {
                    assertThat(packageGroup.packageName()).isEqualTo("org.example.legacy");
                    assertThat(packageGroup.classes()).singleElement()
                            .satisfies(classGroup -> {
                                assertThat(classGroup.className()).isEqualTo("LegacyProcessor");
                                assertThat(classGroup.methods()).singleElement()
                                        .satisfies(methodGroup -> {
                                            assertThat(methodGroup.methodName()).isEqualTo("run");
                                            assertThat(methodGroup.issues()).containsExactly(issue);
                                        });
                            });
                });
    }

    @Test
    void emptyReportHasNoSymbolGroups() {
        assertThat(ConditionValidationReport.empty().symbolGroups()).isEmpty();
    }

    @Test
    void keepsSameLocationAndExpressionWhenSourceContextDiffers() {
        SourceLocation sharedLocation = new SourceLocation("org.example.Shared", "run", 12);
        ConditionValidationIssue first = issue(
                sharedLocation,
                "SharedType.enabled()",
                "SharedType",
                new SourceContext(
                        "org.example.first",
                        "src/main/java/org/example/first/Shared.java",
                        "org.example.first.Shared",
                        "Shared",
                        "run",
                        "run()"));
        ConditionValidationIssue second = issue(
                sharedLocation,
                "SharedType.enabled()",
                "SharedType",
                new SourceContext(
                        "org.example.second",
                        "src/main/java/org/example/second/Shared.java",
                        "org.example.second.Shared",
                        "Shared",
                        "run",
                        "run()"));

        ConditionValidationReport report = new ConditionValidationReport(List.of(first, second));

        assertThat(report.issues()).containsExactly(first, second);
    }

    @Test
    void removesExactTechnicalDuplicates() {
        ConditionValidationIssue first = issue(
                "DuplicateType",
                "org.example.duplicates",
                "DuplicateProcessor",
                "run",
                21);
        ConditionValidationIssue second = issue(
                "DuplicateType",
                "org.example.duplicates",
                "DuplicateProcessor",
                "run",
                21);

        ConditionValidationReport report = new ConditionValidationReport(List.of(first, second));

        assertThat(report.issues()).containsExactly(first);
    }

    private static ConditionValidationIssue issue(String symbol,
                                                  String packageName,
                                                  String className,
                                                  String methodName,
                                                  int line) {
        SourceContext sourceContext = new SourceContext(
                packageName,
                "src/main/java/" + packageName.replace('.', '/') + "/" + className + ".java",
                packageName + "." + className,
                className,
                methodName,
                methodName + "()");
        return new ConditionValidationIssue(
                new SourceLocation(packageName + "." + className, methodName, line),
                symbol + ".value()",
                symbol,
                RuleTemplate.IF_TRUE,
                ConditionResolutionStatus.UNRESOLVED,
                "Test diagnostic.",
                sourceContext);
    }

    private static ConditionValidationIssue issue(SourceLocation location,
                                                  String expression,
                                                  String symbol,
                                                  SourceContext sourceContext) {
        return new ConditionValidationIssue(
                location,
                expression,
                symbol,
                RuleTemplate.IF_TRUE,
                ConditionResolutionStatus.UNRESOLVED,
                "Test diagnostic.",
                sourceContext);
    }
}
