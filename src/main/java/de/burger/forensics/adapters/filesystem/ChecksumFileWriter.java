package de.burger.forensics.adapters.filesystem;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.port.out.ChecksumFilePort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes sha256sum-compatible checksum files.
 */
public final class ChecksumFileWriter implements ChecksumFilePort {

    @Override
    public void write(Path checksumsFile, List<ArtifactChecksum> checksums) {
        Objects.requireNonNull(checksumsFile, "Checksums file must not be null.");
        Objects.requireNonNull(checksums, "Checksums must not be null.");
        try {
            Path parent = checksumsFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(checksumsFile, content(checksums), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write checksums file " + checksumsFile + ".", e);
        }
    }

    private static String content(List<ArtifactChecksum> checksums) {
        return checksums.stream()
                .sorted(Comparator.comparing(ArtifactChecksum::path))
                .map(checksum -> checksum.sha256() + "  " + checksum.path())
                .collect(Collectors.joining(System.lineSeparator(), "", System.lineSeparator()));
    }
}
