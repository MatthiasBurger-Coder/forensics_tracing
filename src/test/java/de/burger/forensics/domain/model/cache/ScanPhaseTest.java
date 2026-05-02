package de.burger.forensics.domain.model.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScanPhaseTest {

    @Test
    void containsRequiredProfilingPhasesInStableOrder() {
        assertThat(ScanPhase.values()).containsExactly(
                ScanPhase.SOURCE_FILE_DISCOVERY,
                ScanPhase.FINGERPRINT_CALCULATION,
                ScanPhase.TYPE_SOLVER_SETUP,
                ScanPhase.JAVA_PARSER_PARSE,
                ScanPhase.PACKAGE_EXTRACTION,
                ScanPhase.METHOD_DISCOVERY,
                ScanPhase.CONTEXT_CREATION,
                ScanPhase.DEPENDENCY_EXTRACTION,
                ScanPhase.EVENT_EXTRACTION,
                ScanPhase.CONDITION_RENDERING,
                ScanPhase.SYMBOL_RESOLUTION,
                ScanPhase.CACHE_READ,
                ScanPhase.CACHE_WRITE,
                ScanPhase.RULE_RENDERING,
                ScanPhase.BTM_FILE_WRITING);
    }
}
