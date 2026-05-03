package de.burger.forensics.application.service;

import de.burger.forensics.application.AnalysisContext;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.entry.MethodEntry;
import de.burger.forensics.domain.port.out.ClockPort;
import de.burger.forensics.domain.port.out.CodeScanPort;
import de.burger.forensics.domain.port.out.LogPort;
import de.burger.forensics.domain.port.out.RuleRenderPort;
import de.burger.forensics.domain.strategy.ConditionStrategy;
import de.burger.forensics.domain.strategy.StrategyFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GenerateRulesUseCaseTest {

    @Test
    void generateAddsEntryExitAndBuildsMethodContext() {
        List<ScanEvent> events = List.of(
            event("com.example.Foo", "doWork", 10, "(String name, int count)", RuleTemplate.IF_TRUE, "x > 1", "java", "boolean"),
            event("com.example.Foo", "doWork", 12, "(String name, int count)", RuleTemplate.SWITCH, "flag", "java", "int"),
            event("com.example.Foo", "doWork", 13, "(String name, int count)", RuleTemplate.SWITCH_CASE, "case", "java", "int"),
            event("com.example.Foo", "doWork", 14, "(String name, int count)", RuleTemplate.RETURN, "result", "java", "int"),
            event("com.example.Foo", "doWork", 15, "(String name, int count)", RuleTemplate.THROW, "boom", "java", "void"),
            event("com.example.Bar", "skipMe", 20, "(String name)", RuleTemplate.IF_FALSE, "x < 0", "kotlin", "boolean"),
            event("com.other.Baz", "skipMe", 30, "(String name)", RuleTemplate.IF_FALSE, "x < 0", "java", "boolean"),
            event("com.example.Qux", "withEntry", 5, null, RuleTemplate.METHOD_ENTER, null, "java", "void"),
            event("com.example.Qux", "withEntry", 6, null, RuleTemplate.METHOD_EXIT, null, "java", "void")
        );

        GenerationRequest request = new GenerationRequest(
            Path.of("/tmp/project"),
            "com.example.SafeEval",
            true,
            true,
            List.of("com.example"),
            0,
            false,
            List.of()
        );

        RuleGenerationResult result = useCase(events).generate(request);

        assertThat(result.renderedRules()).anyMatch(rule -> rule.contains("METHOD_ENTER|doWork"));
        assertThat(result.renderedRules()).anyMatch(rule -> rule.contains("METHOD_EXIT|doWork"));
        assertThat(result.renderedRules()).anyMatch(rule -> rule.contains("IF_TRUE|doWork|com.example.SafeEval.eval"));
        assertThat(result.renderedRules()).noneMatch(rule -> rule.contains("skipMe"));

        AnalysisContext context = result.context();
        MethodEntry methodEntry = context.getMethodEntries().stream()
            .filter(entry -> entry.methodName().equals("doWork"))
            .findFirst()
            .orElseThrow();
        assertThat(methodEntry.parameterTypes()).containsExactly("String", "int");

        MethodEntry entryMethod = context.getMethodEntries().stream()
            .filter(entry -> entry.methodName().equals("withEntry"))
            .findFirst()
            .orElseThrow();
        assertThat(entryMethod.parameterTypes()).isEmpty();
    }

    @Test
    void generateFiltersRulesBelowBranchThreshold() {
        List<ScanEvent> events = List.of(
            event("com.example.Foo", "branchy", 10, "(String name)", RuleTemplate.IF_TRUE, "x > 1", "java", "boolean"),
            event("com.example.Foo", "branchy", 11, "(String name)", RuleTemplate.IF_FALSE, "x > 1", "java", "boolean"),
            event("com.example.Bar", "simple", 20, "(String name)", RuleTemplate.RETURN, "result", "java", "int")
        );

        GenerationRequest request = new GenerationRequest(
            Path.of("/tmp/project"),
            GenerationRequest.DEFAULT_HELPER_FQCN,
            false,
            false,
            List.of("com.example"),
            2,
            false,
            List.of()
        );

        RuleGenerationResult result = useCase(events).generate(request);

        assertThat(result.renderedRules())
            .isNotEmpty()
            .allMatch(rule -> rule.contains("|branchy|"));
    }

    @Test
    void methodWithMultipleReturnsShouldNotGenerateMultipleGenericExitReturnRules() {
        List<ScanEvent> events = List.of(
            event("com.example.Foo", "map", 10, "(int value)", RuleTemplate.RETURN, "zero", "java", "String"),
            event("com.example.Foo", "map", 12, "(int value)", RuleTemplate.RETURN, "positive", "java", "String")
        );

        GenerationRequest request = new GenerationRequest(
            Path.of("/tmp/project"),
            GenerationRequest.DEFAULT_HELPER_FQCN,
            false,
            true,
            List.of("com.example"),
            0,
            false,
            List.of()
        );

        RuleGenerationResult result = useCase(events).generate(request);

        assertThat(result.renderedRules())
            .filteredOn(rule -> rule.startsWith("RETURN|map|"))
            .containsExactly("RETURN|map|zero");
        assertThat(result.renderedRules())
            .filteredOn(rule -> rule.startsWith("METHOD_EXIT|map|"))
            .hasSize(1);
    }

    @Test
    void generateIgnoresManualOnlyTemplatesWhileKeepingSupportedEvents() {
        List<ScanEvent> events = List.of(
            event("com.example.Foo", "supported", 10, "(String name)", RuleTemplate.IF_TRUE, "x > 1", "java", "boolean"),
            event("com.example.Foo", "supported", 11, "(String name)", RuleTemplate.JDBC_EXECUTE, "ignored", "java", "void"),
            event("com.example.Bar", "ignored", 20, "(String name)", RuleTemplate.THREAD_LIFECYCLE, "ignored", "java", "void")
        );

        GenerationRequest request = new GenerationRequest(
            Path.of("/tmp/project"),
            GenerationRequest.DEFAULT_HELPER_FQCN,
            false,
            false,
            List.of("com.example"),
            0,
            false,
            List.of()
        );

        RuleGenerationResult result = useCase(events).generate(request);

        assertThat(result.renderedRules())
            .containsExactly("IF_TRUE|supported|x > 1")
            .doesNotContain("JDBC_EXECUTE|supported|ignored", "THREAD_LIFECYCLE|ignored|ignored");
    }

    @Test
    void generateReportsUnresolvedSimpleTypeReferencesWithoutFailingByDefault() {
        List<ScanEvent> events = List.of(
            event("com.example.Foo", "usesImportedType", 10, "(Object deploymentUnit)", RuleTemplate.IF_TRUE,
                "DeploymentTypeMarker.isType(DeploymentType.EAR, $1)", "java", "boolean")
        );

        GenerationRequest request = new GenerationRequest(
            Path.of("/tmp/project"),
            GenerationRequest.DEFAULT_HELPER_FQCN,
            false,
            false,
            List.of("com.example"),
            0,
            false,
            List.of()
        );

        RuleGenerationResult result = useCase(events).generate(request);

        assertThat(result.validationReport().issues())
            .extracting(issue -> issue.symbol())
            .containsExactly("DeploymentTypeMarker", "DeploymentType");
        assertThat(result.context().getWarnings())
            .extracting(warning -> warning.source())
            .containsExactly("condition-validation", "condition-validation");
    }

    @Test
    void generateFailsForUnresolvedSimpleTypeReferencesWhenStrictValidationIsEnabled() {
        List<ScanEvent> events = List.of(
            event("com.example.Foo", "usesImportedType", 10, "(Object deploymentUnit)", RuleTemplate.IF_TRUE,
                "DeploymentTypeMarker.isType(DeploymentType.EAR, $1)", "java", "boolean")
        );

        GenerationRequest request = new GenerationRequest(
            Path.of("/tmp/project"),
            GenerationRequest.DEFAULT_HELPER_FQCN,
            false,
            false,
            List.of("com.example"),
            0,
            true,
            List.of()
        );

        assertThatThrownBy(() -> useCase(events).generate(request))
            .isInstanceOf(ConditionValidationException.class)
            .hasMessageContaining("Condition validation failed with 2 unresolved type reference warning(s)");
    }

    private static GenerateRulesUseCase useCase(List<ScanEvent> events) {
        CodeScanPort scanner = root -> events.stream();
        RuleRenderPort renderer = rule -> rule.type() + "|" + rule.location().method() + "|" + rule.condition();
        ClockPort clock = () -> Instant.parse("2025-01-01T00:00:00Z");
        LogPort log = mock(LogPort.class);
        StrategyFactory strategyFactory = new StrategyFactory() {
            @Override
            public ConditionStrategy from(String rawExpression, RuleTemplate template, String returnType) {
                return () -> Objects.toString(rawExpression, "");
            }
        };
        return new GenerateRulesUseCase(scanner, renderer, clock, log, strategyFactory);
    }

    private static ScanEvent event(String fqcn,
                                   String method,
                                   int line,
                                   String signature,
                                   RuleTemplate kind,
                                   String condition,
                                   String language,
                                   String returnType) {
        return new ScanEvent(new SourceLocation(fqcn, method, line), signature, kind, condition, language, returnType);
    }
}
