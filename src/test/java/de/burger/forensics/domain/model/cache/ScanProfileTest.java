package de.burger.forensics.domain.model.cache;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanProfileTest {

    @Test
    void aggregatesCountersAndPhaseDurations() {
        ScanProfile first = new ScanProfile(
                Map.of(ScanPhase.FINGERPRINT_CALCULATION, Duration.ofMillis(3),
                        ScanPhase.JAVA_PARSER_PARSE, Duration.ofMillis(7)),
                1, 1, 0, 1, 0, 2, 3, 4);
        ScanProfile second = new ScanProfile(
                Map.of(ScanPhase.FINGERPRINT_CALCULATION, Duration.ofMillis(5),
                        ScanPhase.CACHE_READ, Duration.ofMillis(2)),
                2, 1, 2, 0, 1, 5, 6, 7);

        ScanProfile aggregated = first.plus(second);

        assertThat(aggregated.totalFiles()).isEqualTo(3);
        assertThat(aggregated.parsedFiles()).isEqualTo(2);
        assertThat(aggregated.cacheHitFiles()).isEqualTo(2);
        assertThat(aggregated.cacheMissFiles()).isEqualTo(1);
        assertThat(aggregated.failedFiles()).isEqualTo(1);
        assertThat(aggregated.totalMethods()).isEqualTo(7);
        assertThat(aggregated.totalEvents()).isEqualTo(9);
        assertThat(aggregated.totalDependencies()).isEqualTo(11);
        assertThat(aggregated.phaseDurations())
                .containsEntry(ScanPhase.FINGERPRINT_CALCULATION, Duration.ofMillis(8))
                .containsEntry(ScanPhase.JAVA_PARSER_PARSE, Duration.ofMillis(7))
                .containsEntry(ScanPhase.CACHE_READ, Duration.ofMillis(2));
    }

    @Test
    void createsEmptyProfile() {
        assertThat(ScanProfile.empty())
                .isEqualTo(new ScanProfile(Map.of(), 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void rejectsNegativeCounters() {
        assertThatThrownBy(() -> new ScanProfile(Map.of(), -1, 0, 0, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanProfile(Map.of(), 0, -1, 0, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanProfile(Map.of(), 0, 0, -1, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanProfile(Map.of(), 0, 0, 0, -1, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanProfile(Map.of(), 0, 0, 0, 0, -1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanProfile(Map.of(), 0, 0, 0, 0, 0, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanProfile(Map.of(), 0, 0, 0, 0, 0, 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanProfile(Map.of(), 0, 0, 0, 0, 0, 0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingProfileDuringAggregation() {
        assertThatThrownBy(() -> ScanProfile.empty().plus(null))
                .isInstanceOf(NullPointerException.class);
    }
}
