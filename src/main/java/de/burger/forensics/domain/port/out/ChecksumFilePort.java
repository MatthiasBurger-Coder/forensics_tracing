package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;

import java.nio.file.Path;
import java.util.List;

/**
 * Writes a checksum artifact for generated analysis files.
 */
public interface ChecksumFilePort {

    void write(Path checksumsFile, List<ArtifactChecksum> checksums);
}
