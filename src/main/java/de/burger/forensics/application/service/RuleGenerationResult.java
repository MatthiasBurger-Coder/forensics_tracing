package de.burger.forensics.application.service;

import de.burger.forensics.application.AnalysisContext;
import de.burger.forensics.domain.validation.ConditionValidationReport;
import java.util.List;

/**
 * Value object describing generated rule output.
 */
public record RuleGenerationResult(List<String> renderedRules,
                                   AnalysisContext context,
                                   ConditionValidationReport validationReport) {
}
