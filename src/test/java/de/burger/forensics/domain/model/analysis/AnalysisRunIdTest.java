package de.burger.forensics.domain.model.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisRunIdTest {

    @Test
    void rejectsNullAndBlankValues() {
        assertThatThrownBy(() -> new AnalysisRunId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> new AnalysisRunId(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void createsRandomAndDeterministicIds() {
        assertThat(AnalysisRunId.random().value()).isNotBlank();
        assertThat(AnalysisRunId.deterministic("same")).isEqualTo(AnalysisRunId.deterministic("same"));
        assertThat(AnalysisRunId.deterministic("same")).isNotEqualTo(AnalysisRunId.deterministic("other"));
    }
}
