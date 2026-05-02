package de.burger.forensics.adaptersupport.javaparser;

import de.burger.forensics.domain.model.cache.SourceFileFingerprint;
import de.burger.forensics.domain.model.cache.SourceFileSnapshot;
import de.burger.forensics.domain.port.out.SourceFingerprintPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates SHA-256 based source snapshots for JavaParser cache entries.
 */
public final class DefaultSourceFingerprintPort implements SourceFingerprintPort {

    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    @Override
    public SourceFileSnapshot snapshot(Path rootPath, Path sourceFile) {
        Objects.requireNonNull(rootPath, "Root path must not be null.");
        Objects.requireNonNull(sourceFile, "Source file must not be null.");

        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        try {
            return new SourceFileSnapshot(
                    normalizedRoot,
                    relativePath(normalizedRoot, normalizedSource),
                    normalizedSource,
                    new SourceFileFingerprint(ALGORITHM, fingerprint(sourceFile)),
                    Files.size(sourceFile),
                    lastModifiedAt(sourceFile),
                    true,
                    Optional.empty());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to fingerprint source file " + sourceFile, exception);
        }
    }

    private String fingerprint(Path sourceFile) throws IOException {
        MessageDigest digest = messageDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(sourceFile)) {
            int read = input.read(buffer);
            while (read >= 0) {
                digest.update(buffer, 0, read);
                read = input.read(buffer);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required fingerprint algorithm is unavailable: " + ALGORITHM, exception);
        }
    }

    private Instant lastModifiedAt(Path sourceFile) throws IOException {
        return Files.getLastModifiedTime(sourceFile).toInstant();
    }

    private String relativePath(Path normalizedRoot, Path normalizedSource) {
        Path base = Files.isRegularFile(normalizedRoot) ? normalizedRoot.getParent() : normalizedRoot;
        if (base != null && normalizedSource.startsWith(base)) {
            return normalizeSeparators(base.relativize(normalizedSource).toString());
        }
        return normalizedSource.getFileName().toString();
    }

    private String normalizeSeparators(String path) {
        return path.replace('\\', '/');
    }
}
