package de.burger.forensics.adapters.filesystem;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.port.out.ArtifactChecksumPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Computes SHA-256 metadata for generated analysis artifacts.
 */
public final class ArtifactChecksumService implements ArtifactChecksumPort {

    @Override
    public ArtifactChecksum checksumFile(Path baseDirectory, Path file, String type) {
        Objects.requireNonNull(baseDirectory, "Base directory must not be null.");
        Objects.requireNonNull(file, "Artifact file must not be null.");
        Objects.requireNonNull(type, "Artifact type must not be null.");
        try {
            return new ArtifactChecksum(
                    relativePath(baseDirectory, file),
                    type,
                    sha256(file),
                    Files.size(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to checksum artifact " + file + ".", e);
        }
    }

    @Override
    public ArtifactChecksum checksumDirectory(Path baseDirectory, Path directory, String type) {
        Objects.requireNonNull(baseDirectory, "Base directory must not be null.");
        Objects.requireNonNull(directory, "Artifact directory must not be null.");
        Objects.requireNonNull(type, "Artifact type must not be null.");
        List<Path> files = regularFiles(directory);
        MessageDigest digest = sha256Digest();
        long size = 0L;
        for (Path file : files) {
            ArtifactChecksum checksum = checksumFile(directory, file, "analysis-store-file");
            digest.update(relativePath(directory, file).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(checksum.sha256().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(checksum.sizeBytes()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            size += checksum.sizeBytes();
        }
        return new ArtifactChecksum(
                relativePath(baseDirectory, directory),
                type,
                HexFormat.of().formatHex(digest.digest()),
                size);
    }

    @Override
    public List<ArtifactChecksum> checksumFiles(Path baseDirectory, Path artifactRoot, String type) {
        if (Files.isRegularFile(artifactRoot)) {
            return List.of(checksumFile(baseDirectory, artifactRoot, type));
        }
        return regularFiles(artifactRoot).stream()
                .map(file -> checksumFile(baseDirectory, file, type))
                .toList();
    }

    private static List<Path> regularFiles(Path artifactRoot) {
        if (!Files.exists(artifactRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(artifactRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk artifact root " + artifactRoot + ".", e);
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static String relativePath(Path baseDirectory, Path artifact) {
        Path normalizedBase = baseDirectory.toAbsolutePath().normalize();
        Path normalizedArtifact = artifact.toAbsolutePath().normalize();
        Path relative = normalizedArtifact.startsWith(normalizedBase)
                ? normalizedBase.relativize(normalizedArtifact)
                : normalizedArtifact.getFileName();
        return relative.toString().replace('\\', '/');
    }
}
