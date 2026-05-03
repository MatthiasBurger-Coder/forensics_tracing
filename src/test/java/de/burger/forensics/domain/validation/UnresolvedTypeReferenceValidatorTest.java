package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnresolvedTypeReferenceValidatorTest {

    private final UnresolvedTypeReferenceValidator validator = new UnresolvedTypeReferenceValidator();

    @Test
    void reportsImportedSimpleTypeNamesInConditions() {
        ScanEvent event = event("DeploymentTypeMarker.isType(DeploymentType.EAR, $deploymentUnit)");

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues())
                .extracting(ConditionValidationIssue::symbol)
                .containsExactly("DeploymentTypeMarker", "DeploymentType");
    }

    @Test
    void acceptsAlreadyFullyQualifiedTypeNames() {
        ScanEvent event = event(
                "org.example.DeploymentTypeMarker.isType(org.example.DeploymentType.EAR, $1)");

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void acceptsJavaLangTypesAndPlainUppercaseIdentifiersWithoutMemberAccess() {
        ScanEvent event = event("String.valueOf($1).equals(DeploymentType)");

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void reportsSimpleTypeNamesWithWhitespaceBeforeMemberAccess() {
        ScanEvent event = event("  DeploymentType   .EAR == null");

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues())
                .extracting(ConditionValidationIssue::symbol)
                .containsExactly("DeploymentType");
    }

    @Test
    void ignoresBytemanParametersAndLocalVariables() {
        ScanEvent event = event("$deploymentUnit != null && $1 != null && $LocalValue.ready()");

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void ignoresBlankExpressions() {
        ConditionValidationReport report = validator.validate(List.of(event(null), event(" ")));

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void ignoresTypeLikeTextInsideStringLiterals() {
        ScanEvent event = event("\"DeploymentType.EAR\".equals($name)");

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void ignoresTypeLikeTextInsideEscapedStringAndCharLiterals() {
        ScanEvent event = event("'\\'' == $quote && \"DeploymentType.EAR\\\"\".equals($text)");

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void mergeDeduplicatesIssuesAcrossReports() {
        ConditionValidationReport left = validator.validate(List.of(event("DeploymentType.EAR != null")));
        ConditionValidationReport right = validator.validate(List.of(
                event("DeploymentType.EAR != null"),
                event("SubsystemResourceRegistration.of(\"infinispan\") != null")));

        ConditionValidationReport merged = left.merge(right);

        assertThat(merged.issues())
                .extracting(ConditionValidationIssue::symbol)
                .containsExactly("DeploymentType", "SubsystemResourceRegistration");
    }

    private static ScanEvent event(String condition) {
        return new ScanEvent(
                new SourceLocation("com.example.Sample", "run", 42),
                "(Object deploymentUnit)",
                RuleTemplate.IF_TRUE,
                condition,
                "java",
                "boolean");
    }
}
