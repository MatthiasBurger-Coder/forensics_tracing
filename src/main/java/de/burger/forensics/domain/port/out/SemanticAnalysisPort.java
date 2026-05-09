package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.semantic.SemanticAnalysisRequest;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;

/**
 * Runs provider-neutral semantic source analysis.
 */
public interface SemanticAnalysisPort {

    SemanticAnalysisResult analyze(SemanticAnalysisRequest request);
}
