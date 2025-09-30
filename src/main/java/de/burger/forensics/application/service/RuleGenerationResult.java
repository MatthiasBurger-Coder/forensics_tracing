package de.burger.forensics.application.service;

import java.util.List;

/**
 * Value object describing generated rule output.
 */
public record RuleGenerationResult(List<String> renderedRules) {
}
