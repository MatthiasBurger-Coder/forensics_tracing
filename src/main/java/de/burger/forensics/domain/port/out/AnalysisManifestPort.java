package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildIdentity;

import java.nio.file.Path;
import java.util.List;

/**
 * Writes manifest metadata for a generated analysis package.
 */
public interface AnalysisManifestPort {

    void write(Path manifestFile, BuildIdentity identity, List<ArtifactChecksum> artifacts);
}
