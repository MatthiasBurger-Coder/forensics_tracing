package de.burger.forensics.domain.model.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisValueObjectTest {

    @Test
    void rejectsBlankSchemaVersions() {
        assertThatThrownBy(() -> new AnalysisSchemaVersion(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> new AnalysisSchemaVersion(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        assertThat(new AnalysisSchemaVersion("2").value()).isEqualTo("2");
    }

    @Test
    void rejectsBlankBuildIds() {
        assertThatThrownBy(() -> new BuildId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> new BuildId(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        assertThat(new BuildId("build-1").value()).isEqualTo("build-1");
    }

    @Test
    void rejectsBlankSourceFingerprints() {
        assertThatThrownBy(() -> new SourceFingerprint(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> new SourceFingerprint(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        assertThat(new SourceFingerprint("sha256:abc").value()).isEqualTo("sha256:abc");
    }

    @Test
    void rejectsInvalidSourceFileSnapshots() {
        assertThatThrownBy(() -> new SourceFileSnapshot(null, "absolute", "sha", 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Relative source path");
        assertThatThrownBy(() -> new SourceFileSnapshot(" ", "absolute", "sha", 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Relative source path");
        assertThatThrownBy(() -> new SourceFileSnapshot("relative", null, "sha", 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Absolute source path");
        assertThatThrownBy(() -> new SourceFileSnapshot("relative", " ", "sha", 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Absolute source path");
        assertThatThrownBy(() -> new SourceFileSnapshot("relative", "absolute", null, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source checksum");
        assertThatThrownBy(() -> new SourceFileSnapshot("relative", "absolute", " ", 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source checksum");
        assertThatThrownBy(() -> new SourceFileSnapshot("relative", "absolute", "sha", -1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source file size");
        assertThatThrownBy(() -> new SourceFileSnapshot("relative", "absolute", "sha", 1L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Last modified timestamp");

        SourceFileSnapshot snapshot = new SourceFileSnapshot("relative", "absolute", "sha", 1L, 2L);

        assertThat(snapshot.relativePath()).isEqualTo("relative");
        assertThat(snapshot.absolutePath()).isEqualTo("absolute");
        assertThat(snapshot.sha256()).isEqualTo("sha");
        assertThat(snapshot.fileSize()).isEqualTo(1L);
        assertThat(snapshot.lastModifiedEpochMillis()).isEqualTo(2L);
    }
}
