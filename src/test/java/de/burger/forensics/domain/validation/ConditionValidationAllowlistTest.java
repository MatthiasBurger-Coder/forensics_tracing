package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.ConditionResolutionStatus;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.SourceContext;
import de.burger.forensics.domain.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionValidationAllowlistTest {

    @Test
    void suppressesGlobalSymbolsAndKeepsSuppressedCountSeparate() {
        ConditionValidationIssue allowed = issue("DeploymentType", "org.example.deployment");
        ConditionValidationIssue reported = issue("OtherType", "org.example.deployment");
        ConditionValidationAllowlist allowlist = new ConditionValidationAllowlist(List.of(
                ConditionValidationAllowlist.Entry.global("DeploymentType", "Known runtime enum.")));

        ConditionValidationReport report = allowlist.apply(new ConditionValidationReport(List.of(allowed, reported)));

        assertThat(report.issues()).containsExactly(reported);
        assertThat(report.suppressedIssues()).containsExactly(allowed);
        assertThat(report.suppressedIssueCount()).isEqualTo(1);
    }

    @Test
    void suppressesPackageScopedSymbolsOnlyInsideMatchingPackage() {
        ConditionValidationIssue allowed = issue("DeploymentType", "org.example.allowed");
        ConditionValidationIssue reported = issue("DeploymentType", "org.example.reported");
        ConditionValidationAllowlist allowlist = new ConditionValidationAllowlist(List.of(
                ConditionValidationAllowlist.Entry.packageScoped(
                        "DeploymentType",
                        "org.example.allowed",
                        "Known type in this source package.")));

        ConditionValidationReport report = allowlist.apply(new ConditionValidationReport(List.of(allowed, reported)));

        assertThat(report.issues()).containsExactly(reported);
        assertThat(report.suppressedIssues()).containsExactly(allowed);
    }

    @Test
    void requiresAllowlistReasons() {
        assertThatThrownBy(() -> ConditionValidationAllowlist.Entry.global("DeploymentType", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    private static ConditionValidationIssue issue(String symbol, String packageName) {
        SourceContext sourceContext = new SourceContext(
                packageName,
                "src/main/java/" + packageName.replace('.', '/') + "/Sample.java",
                packageName + ".Sample",
                "Sample",
                "run",
                "run()");
        return new ConditionValidationIssue(
                new SourceLocation(packageName + ".Sample", "run", 42),
                symbol + ".value()",
                symbol,
                RuleTemplate.IF_TRUE,
                ConditionResolutionStatus.UNRESOLVED,
                "Test diagnostic.",
                sourceContext);
    }
}
