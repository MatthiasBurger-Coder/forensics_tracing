package de.burger.forensics.application.service;

import de.burger.forensics.application.AnalysisContext;
import java.util.List;

/**
 * Value object describing generated rule output.
 */
public record RuleGenerationResult(List<String> renderedRules, AnalysisContext context) {
}
