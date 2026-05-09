package de.burger.forensics.domain.model.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisStoreCleanupPolicyTest {

    @Test
    void resolvesBlankValuesToDefaultPolicy() {
        assertThat(AnalysisStoreCleanupPolicy.from(null)).isEqualTo(AnalysisStoreCleanupPolicy.DEFAULT);
        assertThat(AnalysisStoreCleanupPolicy.from(" ")).isEqualTo(AnalysisStoreCleanupPolicy.DEFAULT);
    }

    @Test
    void resolvesTrimmedPolicyNames() {
        assertThat(AnalysisStoreCleanupPolicy.from("KEEP_ALWAYS")).isEqualTo(AnalysisStoreCleanupPolicy.KEEP_ALWAYS);
        assertThat(AnalysisStoreCleanupPolicy.from(" KEEP_ON_FAILURE ")).isEqualTo(AnalysisStoreCleanupPolicy.KEEP_ON_FAILURE);
    }

    @Test
    void rejectsUnknownPolicyNames() {
        assertThatThrownBy(() -> AnalysisStoreCleanupPolicy.from("DELETE_ALWAYS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported analysis store cleanup policy");
    }

    @Test
    void mapsSuccessAndFailureDeletionSemantics() {
        assertThat(AnalysisStoreCleanupPolicy.DELETE_ON_SUCCESS.shouldDeleteAfterSuccess()).isTrue();
        assertThat(AnalysisStoreCleanupPolicy.KEEP_ON_FAILURE.shouldDeleteAfterSuccess()).isTrue();
        assertThat(AnalysisStoreCleanupPolicy.KEEP_ON_SUCCESS.shouldDeleteAfterSuccess()).isFalse();
        assertThat(AnalysisStoreCleanupPolicy.KEEP_ALWAYS.shouldDeleteAfterSuccess()).isFalse();

        assertThat(AnalysisStoreCleanupPolicy.DELETE_ON_SUCCESS.shouldDeleteAfterFailure()).isFalse();
        assertThat(AnalysisStoreCleanupPolicy.KEEP_ON_SUCCESS.shouldDeleteAfterFailure()).isFalse();
        assertThat(AnalysisStoreCleanupPolicy.KEEP_ON_FAILURE.shouldDeleteAfterFailure()).isFalse();
        assertThat(AnalysisStoreCleanupPolicy.KEEP_ALWAYS.shouldDeleteAfterFailure()).isFalse();
    }
}
