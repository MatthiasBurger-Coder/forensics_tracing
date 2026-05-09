package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisRunStatus;
import de.burger.forensics.domain.model.analysis.BuildIdentity;

/**
 * Stores analysis run lifecycle metadata.
 */
public interface AnalysisRunStorePort {

    void initializeSchema();

    void createAnalysisRun(BuildIdentity identity);

    void updateAnalysisRunStatus(AnalysisRunId analysisRunId, AnalysisRunStatus status);
}
