package de.burger.forensics.domain.model.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFileFingerprintTest {

    @Test
    void treatsSameAlgorithmAndValueAsSameFingerprint() {
        SourceFileFingerprint first = new SourceFileFingerprint("SHA-256", "abc");
        SourceFileFingerprint second = new SourceFileFingerprint("SHA-256", "abc");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void treatsChangedValueAsChangedFingerprint() {
        SourceFileFingerprint original = new SourceFileFingerprint("SHA-256", "abc");
        SourceFileFingerprint changed = new SourceFileFingerprint("SHA-256", "def");

        assertThat(original).isNotEqualTo(changed);
    }

    @Test
    void rejectsMissingAlgorithm() {
        assertThatThrownBy(() -> new SourceFileFingerprint(null, "abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceFileFingerprint(" ", "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingValue() {
        assertThatThrownBy(() -> new SourceFileFingerprint("SHA-256", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceFileFingerprint("SHA-256", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
