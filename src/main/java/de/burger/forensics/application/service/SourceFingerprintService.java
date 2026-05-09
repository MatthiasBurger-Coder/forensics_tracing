package de.burger.forensics.application.service;

import de.burger.forensics.domain.model.analysis.SourceFileSnapshot;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;

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
 * Creates deterministic source snapshots for Java source roots.
 */
public final class SourceFingerprintService {

    public SourceFingerprintResult fingerprint(List<Path> sourceRoots) {
        Objects.requireNonNull(sourceRoots, "Source roots must not be null.");
        List<SourceFileSnapshot> snapshots = sourceRoots.stream()
                .filter(Objects::nonNull)
                .flatMap(this::javaFiles)
                .map(candidate -> snapshot(candidate.root(), candidate.sourceFile()))
                .sorted(Comparator.comparing(SourceFileSnapshot::relativePath)
                        .thenComparing(SourceFileSnapshot::absolutePath))
                .toList();
        return new SourceFingerprintResult(new SourceFingerprint("sha256:" + aggregateHash(snapshots)), snapshots);
    }

    private Stream<SourceCandidate> javaFiles(Path sourceRoot) {
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalizedRoot)) {
            return normalizedRoot.getFileName().toString().endsWith(".java")
                    ? Stream.of(new SourceCandidate(normalizedRoot, normalizedRoot))
                    : Stream.empty();
        }
        if (!Files.isDirectory(normalizedRoot)) {
            return Stream.empty();
        }
        try {
            return Files.walk(normalizedRoot)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> new SourceCandidate(normalizedRoot, path.toAbsolutePath().normalize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk source root " + normalizedRoot + ".", e);
        }
    }

    private SourceFileSnapshot snapshot(Path root, Path sourceFile) {
        try {
            return new SourceFileSnapshot(
                    relativePath(root, sourceFile),
                    sourceFile.toAbsolutePath().normalize().toString(),
                    sha256(sourceFile),
                    Files.size(sourceFile),
                    Files.getLastModifiedTime(sourceFile).toMillis());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fingerprint source file " + sourceFile + ".", e);
        }
    }

    private static String relativePath(Path root, Path sourceFile) {
        Path relative = Files.isRegularFile(root)
                ? sourceFile.getFileName()
                : root.relativize(sourceFile);
        return relative.toString().replace('\\', '/');
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

    private static String aggregateHash(List<SourceFileSnapshot> snapshots) {
        MessageDigest digest = sha256Digest();
        for (SourceFileSnapshot snapshot : snapshots) {
            digest.update(snapshot.relativePath().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(snapshot.sha256().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
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

    private record SourceCandidate(Path root, Path sourceFile) {
    }
}
