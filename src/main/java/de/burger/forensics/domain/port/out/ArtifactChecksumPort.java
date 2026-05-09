package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;

import java.nio.file.Path;
import java.util.List;

/**
 * Computes checksum metadata for generated analysis artifacts.
 */
public interface ArtifactChecksumPort {

    ArtifactChecksum checksumFile(Path baseDirectory, Path file, String type);

    ArtifactChecksum checksumDirectory(Path baseDirectory, Path directory, String type);

    List<ArtifactChecksum> checksumFiles(Path baseDirectory, Path artifactRoot, String type);
}
