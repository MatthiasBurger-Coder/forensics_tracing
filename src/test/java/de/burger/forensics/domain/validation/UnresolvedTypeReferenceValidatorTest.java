package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ConditionDiagnostic;
import de.burger.forensics.domain.model.ConditionResolutionStatus;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.SourceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

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
    void reportsSimpleTypeNamesWithWhitespaceBeforeMemberAccess() {
        ScanEvent event = event("  DeploymentType   .EAR == null");

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues())
                .extracting(ConditionValidationIssue::symbol)
                .containsExactly("DeploymentType");
    }

    @Test
    void ignoresBlankExpressions() {
        ConditionValidationReport report = validator.validate(List.of(event(null), event(" ")));

        assertThat(report.issues()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedConditionCases")
    void acceptsConditionsWithoutUnresolvedTypeIssues(String scenario, List<ScanEvent> events) {
        ConditionValidationReport report = validator.validate(events);

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

    @Test
    void diagnosticMessagesUseSingleLineTruncatedExpressionPreviews() {
        String expression = "DeploymentType.EAR != null &&\n"
                + "DeploymentType.EAR.name().equals(\"x\") && "
                + "DeploymentType.EAR.name().repeat(50).equals(\""
                + "x".repeat(260)
                + "\")";
        ConditionValidationReport report = validator.validate(List.of(event(expression)));

        ConditionValidationIssue issue = report.issues().get(0);

        assertThat(issue.expressionPreview())
                .doesNotContain("\n")
                .hasSizeLessThanOrEqualTo(240)
                .endsWith("...");
        assertThat(issue.message()).doesNotContain("\n");
        assertThat(issue.template()).isEqualTo(RuleTemplate.IF_TRUE);
    }

    @Test
    void usesStructuredDiagnosticsWhenScanEventProvidesThem() {
        SourceLocation location = new SourceLocation("com.example.Sample", "run", 42);
        SourceContext sourceContext = new SourceContext(
                "com.example",
                "src/main/java/com/example/Sample.java",
                "com.example.Sample",
                "Sample",
                "run",
                "run()");
        ConditionDiagnostic diagnostic = new ConditionDiagnostic(
                "DeploymentType",
                "DeploymentType.EAR != null",
                ConditionResolutionStatus.AMBIGUOUS,
                "Multiple wildcard import candidates matched the symbol.",
                location,
                sourceContext);
        ScanEvent event = new ScanEvent(
                location,
                "run()",
                RuleTemplate.IF_TRUE,
                "DeploymentType.EAR != null",
                "java",
                "boolean",
                List.of(diagnostic));

        ConditionValidationReport report = validator.validate(List.of(event));

        assertThat(report.issues()).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.symbol()).isEqualTo("DeploymentType");
                    assertThat(issue.resolutionStatus()).isEqualTo(ConditionResolutionStatus.AMBIGUOUS);
                    assertThat(issue.reason()).isEqualTo("Multiple wildcard import candidates matched the symbol.");
                    assertThat(issue.sourceContext()).isEqualTo(sourceContext);
                });
    }

    private static ScanEvent event(String condition) {
        return event(RuleTemplate.IF_TRUE, condition);
    }

    private static Stream<Arguments> acceptedConditionCases() {
        return Stream.of(
                Arguments.of(
                        "already fully qualified type names",
                        List.of(event("org.example.DeploymentTypeMarker.isType(org.example.DeploymentType.EAR, $1)"))),
                Arguments.of(
                        "java lang types and plain uppercase identifiers without member access",
                        List.of(event("String.valueOf($1).equals(DeploymentType)"))),
                Arguments.of(
                        "Byteman parameters and local variables",
                        List.of(event("$deploymentUnit != null && $1 != null && $LocalValue.ready()"))),
                Arguments.of(
                        "non-executable conditions",
                        List.of(
                                event(RuleTemplate.RETURN, "DeploymentType.EAR"),
                                event(RuleTemplate.SWITCH_CASE, "DeploymentType.EAR"))),
                Arguments.of(
                        "type-like text inside string literals",
                        List.of(event("\"DeploymentType.EAR\".equals($name)")))
        );
    }

    private static ScanEvent event(RuleTemplate template, String condition) {
        return new ScanEvent(
                new SourceLocation("com.example.Sample", "run", 42),
                "(Object deploymentUnit)",
                template,
                condition,
                "java",
                "boolean");
    }
}
