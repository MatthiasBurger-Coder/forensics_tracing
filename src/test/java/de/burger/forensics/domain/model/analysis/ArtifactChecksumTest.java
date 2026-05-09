package de.burger.forensics.domain.model.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactChecksumTest {

    @Test
    void storesArtifactMetadata() {
        ArtifactChecksum checksum = new ArtifactChecksum("forensics.btm", "byteman-rules", "abc123", 42L);

        assertThat(checksum.path()).isEqualTo("forensics.btm");
        assertThat(checksum.type()).isEqualTo("byteman-rules");
        assertThat(checksum.sha256()).isEqualTo("abc123");
        assertThat(checksum.sizeBytes()).isEqualTo(42L);
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new ArtifactChecksum(null, "type", "abc", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ArtifactChecksum("", "type", "abc", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ArtifactChecksum("path", null, "abc", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ArtifactChecksum("path", "", "abc", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ArtifactChecksum("path", "type", null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ArtifactChecksum("path", "type", "", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ArtifactChecksum("path", "type", "abc", -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
