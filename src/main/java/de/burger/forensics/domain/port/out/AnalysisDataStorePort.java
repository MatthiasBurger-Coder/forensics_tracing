package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.SourceFileSnapshot;
import de.burger.forensics.domain.model.entry.MethodEntry;

import java.util.List;
import java.util.Map;

/**
 * Stores raw analysis data linked to an analysis run.
 */
public interface AnalysisDataStorePort {

    void storeSourceFiles(AnalysisRunId analysisRunId, List<SourceFileSnapshot> sourceFiles);

    void storeMethods(AnalysisRunId analysisRunId, List<MethodEntry> methods);

    void storeScanEvents(AnalysisRunId analysisRunId, List<ScanEvent> events);

    void storeRules(AnalysisRunId analysisRunId, List<Rule> rules, Map<String, String> renderedRulesByRuleId);

    void storeArtifactChecksums(AnalysisRunId analysisRunId, List<ArtifactChecksum> checksums);
}
