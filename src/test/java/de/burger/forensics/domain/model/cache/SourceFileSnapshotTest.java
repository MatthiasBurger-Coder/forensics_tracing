package de.burger.forensics.domain.model.cache;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFileSnapshotTest {

    @Test
    void keepsCacheInvalidationState() {
        SourceFileSnapshot snapshot = new SourceFileSnapshot(
                Path.of("/workspace/src/main/java"),
                "sample/Sample.java",
                Path.of("/workspace/src/main/java/sample/Sample.java"),
                new SourceFileFingerprint("SHA-256", "abc"),
                42L,
                Instant.parse("2026-05-02T10:15:30Z"),
                false,
                Optional.of("Parse failed"));

        assertThat(snapshot.rootPath()).isEqualTo(Path.of("/workspace/src/main/java"));
        assertThat(snapshot.relativePath()).isEqualTo("sample/Sample.java");
        assertThat(snapshot.sourcePath()).isEqualTo(Path.of("/workspace/src/main/java/sample/Sample.java"));
        assertThat(snapshot.parseSucceeded()).isFalse();
        assertThat(snapshot.failureMessage()).contains("Parse failed");
    }

    @Test
    void acceptsSuccessfulParseWithoutFailureMessage() {
        SourceFileSnapshot snapshot = new SourceFileSnapshot(
                Path.of("/workspace/src/main/java"),
                "sample/Sample.java",
                Path.of("/workspace/src/main/java/sample/Sample.java"),
                new SourceFileFingerprint("SHA-256", "abc"),
                42L,
                Instant.parse("2026-05-02T10:15:30Z"),
                true,
                Optional.empty());

        assertThat(snapshot.failureMessage()).isEmpty();
    }

    @Test
    void rejectsNegativeSize() {
        Path rootPath = Path.of("/workspace/src/main/java");
        String relativePath = "sample/Sample.java";
        Path sourcePath = Path.of("/workspace/src/main/java/sample/Sample.java");
        SourceFileFingerprint fingerprint = new SourceFileFingerprint("SHA-256", "abc");
        Instant lastModifiedAt = Instant.parse("2026-05-02T10:15:30Z");
        Optional<String> failureMessage = Optional.empty();

        assertThatThrownBy(() -> new SourceFileSnapshot(
                rootPath,
                relativePath,
                sourcePath,
                fingerprint,
                -1L,
                lastModifiedAt,
                true,
                failureMessage))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankRelativePath() {
        Path rootPath = Path.of("/workspace/src/main/java");
        Path sourcePath = Path.of("/workspace/src/main/java/sample/Sample.java");
        SourceFileFingerprint fingerprint = new SourceFileFingerprint("SHA-256", "abc");
        Instant lastModifiedAt = Instant.parse("2026-05-02T10:15:30Z");
        Optional<String> failureMessage = Optional.empty();

        assertThatThrownBy(() -> new SourceFileSnapshot(
                rootPath,
                " ",
                sourcePath,
                fingerprint,
                42L,
                lastModifiedAt,
                true,
                failureMessage))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
