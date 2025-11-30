package de.burger.forensics.infrastructure.rt;

import org.jboss.byteman.rule.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RtTraceHelperTest {

    @BeforeAll
    static void enableRuntime() {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void evalReturnsProvidedValue() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        boolean result = RtTraceHelper.eval("rule-1", "flag", true);

        assertThat(result).isTrue();
    }

    @Test
    void evalPropagatesFalseValue() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        boolean result = RtTraceHelper.eval("rule-2", "flag", false);

        assertThat(result).isFalse();
    }
}
