package de.burger.forensics.adaptersupport.javaparser;

import de.burger.forensics.domain.model.cache.SourceFileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSourceFingerprintPortTest {

    private final DefaultSourceFingerprintPort fingerprintPort = new DefaultSourceFingerprintPort();

    @Test
    void createsSha256SnapshotWithNormalizedRelativePath(@TempDir Path tempDir) throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("sample"));
        Path source = sourceDir.resolve("Sample.java");
        Files.writeString(source, "abc");

        SourceFileSnapshot snapshot = fingerprintPort.snapshot(tempDir, source);

        assertThat(snapshot.rootPath()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(snapshot.sourcePath()).isEqualTo(source.toAbsolutePath().normalize());
        assertThat(snapshot.relativePath()).isEqualTo("sample/Sample.java");
        assertThat(snapshot.fingerprint().algorithm()).isEqualTo("SHA-256");
        assertThat(snapshot.fingerprint().value())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(snapshot.size()).isEqualTo(3L);
        assertThat(snapshot.lastModifiedAt()).isNotNull();
        assertThat(snapshot.parseSucceeded()).isTrue();
        assertThat(snapshot.failureMessage()).isEmpty();
    }

    @Test
    void usesFileNameWhenRootIsTheSourceFile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Single.java");
        Files.writeString(source, "class Single {}");

        SourceFileSnapshot snapshot = fingerprintPort.snapshot(source, source);

        assertThat(snapshot.relativePath()).isEqualTo("Single.java");
    }

    @Test
    void changesFingerprintWhenContentChanges(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, "class Sample {}");
        SourceFileSnapshot before = fingerprintPort.snapshot(tempDir, source);

        Files.writeString(source, "class Sample { void run() {} }");
        SourceFileSnapshot after = fingerprintPort.snapshot(tempDir, source);

        assertThat(after.fingerprint()).isNotEqualTo(before.fingerprint());
    }

    @Test
    void usesFileNameWhenSourceIsOutsideRoot(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path source = Files.writeString(outside.resolve("Sample.java"), "class Sample {}");

        SourceFileSnapshot snapshot = fingerprintPort.snapshot(root, source);

        assertThat(snapshot.relativePath()).isEqualTo("Sample.java");
    }

    @Test
    void wrapsFingerprintIoFailures(@TempDir Path tempDir) {
        Path missingSource = tempDir.resolve("Missing.java");

        assertThatThrownBy(() -> fingerprintPort.snapshot(tempDir, missingSource))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("Failed to fingerprint source file");
    }
}
