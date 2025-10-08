package de.burger.forensics.infrastructure.rt;

import org.jboss.byteman.rule.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RtTraceHelperTest {

    @BeforeAll
    static void enableRuntime() {
        System.setProperty("forensics.rt.enabled", "true");
    }

    @Test
    void evalInvokesSupplierAndReturnsResult() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));
        AtomicBoolean invoked = new AtomicBoolean(false);

        boolean result = helper.eval("rule-1", "flag", () -> {
            invoked.set(true);
            return true;
        });

        assertThat(invoked).isTrue();
        assertThat(result).isTrue();
    }

    @Test
    void evalReturnsFalseWhenSupplierIsNull() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        boolean result = helper.eval("rule-2", "flag", null);

        assertThat(result).isFalse();
    }

    @Test
    void evalSuppressesExceptionsAndReturnsFalse() {
        RtTraceHelper helper = new RtTraceHelper(mock(Rule.class));

        boolean result = helper.eval("rule-3", "flag", () -> {
            throw new IllegalStateException("boom");
        });

        assertThat(result).isFalse();
    }
}
