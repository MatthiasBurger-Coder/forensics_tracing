package de.burger.forensics.application.service;

import de.burger.forensics.domain.model.semantic.SemanticAnalysisRequest;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.port.out.SemanticAnalysisPort;
import de.burger.forensics.domain.port.out.SemanticAnalysisStorePort;

import java.util.Objects;

/**
 * Coordinates semantic analysis and persistent semantic import storage.
 */
public final class AnalyzeSemanticsUseCase {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final SemanticAnalysisPort analysisPort;
    private final SemanticAnalysisStorePort storePort;

    public AnalyzeSemanticsUseCase(SemanticAnalysisPort analysisPort, SemanticAnalysisStorePort storePort) {
        this.analysisPort = Objects.requireNonNull(analysisPort, "Semantic analysis port must not be null.");
        this.storePort = Objects.requireNonNull(storePort, "Semantic analysis store port must not be null.");
    }

    public SemanticAnalysisResult analyze(SemanticAnalysisRequest request) {
        Objects.requireNonNull(request, "Semantic analysis request must not be null.");
        SemanticAnalysisResult result = analysisPort.analyze(request);
        try {
            storePort.createSemanticImportRun(request.identity().analysisRunId(), result);
            storePort.storeSemanticGraph(request.identity().analysisRunId(), result);
            storePort.storeSemanticAnchors(request.identity().analysisRunId(), result.anchors());
            storePort.updateSemanticImportStatus(
                    request.identity().analysisRunId(),
                    result.semanticFingerprint(),
                    STATUS_COMPLETED);
            return result;
        } catch (RuntimeException exception) {
            markImportFailed(request, result);
            throw exception;
        }
    }

    private void markImportFailed(SemanticAnalysisRequest request, SemanticAnalysisResult result) {
        try {
            storePort.updateSemanticImportStatus(
                    request.identity().analysisRunId(),
                    result.semanticFingerprint(),
                    STATUS_FAILED);
        } catch (RuntimeException ignored) {
            // Preserve the original import failure.
        }
    }
}
