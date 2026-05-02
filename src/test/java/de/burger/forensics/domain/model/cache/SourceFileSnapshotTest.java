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
        assertThatThrownBy(() -> new SourceFileSnapshot(
                Path.of("/workspace/src/main/java"),
                "sample/Sample.java",
                Path.of("/workspace/src/main/java/sample/Sample.java"),
                new SourceFileFingerprint("SHA-256", "abc"),
                -1L,
                Instant.parse("2026-05-02T10:15:30Z"),
                true,
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankRelativePath() {
        assertThatThrownBy(() -> new SourceFileSnapshot(
                Path.of("/workspace/src/main/java"),
                " ",
                Path.of("/workspace/src/main/java/sample/Sample.java"),
                new SourceFileFingerprint("SHA-256", "abc"),
                42L,
                Instant.parse("2026-05-02T10:15:30Z"),
                true,
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
