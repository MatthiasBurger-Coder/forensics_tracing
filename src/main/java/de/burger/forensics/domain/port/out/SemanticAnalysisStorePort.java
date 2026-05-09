package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;

import java.util.List;

/**
 * Stores provider-neutral semantic enrichment data for an analysis run.
 */
public interface SemanticAnalysisStorePort {

    void createSemanticImportRun(AnalysisRunId analysisRunId, SemanticAnalysisResult result);

    void storeSemanticGraph(AnalysisRunId analysisRunId, SemanticAnalysisResult result);

    void storeSemanticAnchors(AnalysisRunId analysisRunId, List<SemanticAnchor> anchors);

    void updateSemanticImportStatus(AnalysisRunId analysisRunId, String semanticFingerprint, String status);
}
