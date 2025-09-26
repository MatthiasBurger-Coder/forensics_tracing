package org.example.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SafeEvalTest {

    @Test
    void eq_nulls_trueOnlyWhenBothNull() {
        assertThat(SafeEval.ifEq(null, null)).isTrue();
        assertThat(SafeEval.ifEq(null, "x")).isFalse();
        assertThat(SafeEval.ifEq("x", null)).isFalse();
    }

    @Test
    void eq_numbers_robust() {
        assertThat(SafeEval.ifEq(1, 1L)).isTrue();
        assertThat(SafeEval.ifEq(1.0, new BigDecimal("1.0"))).isTrue();
        assertThat(SafeEval.ifEq(1.0, 1.0001)).isFalse();
    }

    @Test
    void eq_enums_byName() {
        enum C { OK, NOK }
        assertThat(SafeEval.ifEq(C.OK, "OK")).isTrue();
        assertThat(SafeEval.ifEq(C.NOK, "OK")).isFalse();
        assertThat(SafeEval.ifEq("OK", C.OK)).isTrue();
    }

    @Test
    void eq_strings_equals() {
        assertThat(SafeEval.ifEq("OK", "OK")).isTrue();
        assertThat(SafeEval.ifEq("OK", "ok")).isFalse();
    }

    @Test
    void instanceOf_checksHierarchyAndInterfaces() {
        class A implements java.io.Serializable {}
        A a = new A();
        assertThat(SafeEval.ifInstanceOf(a, "java.io.Serializable")).isTrue();
        assertThat(SafeEval.ifInstanceOf(a, "java.lang.Number")).isFalse();
        assertThat(SafeEval.ifInstanceOf(null, "java.lang.Object")).isFalse();
    }

    @Test
    void combinators_shortCircuitSemantics() {
        assertThat(SafeEval.and(true, true)).isTrue();
        assertThat(SafeEval.and(true, false)).isFalse();
        assertThat(SafeEval.or(false, true)).isTrue();
        assertThat(SafeEval.or(false, false)).isFalse();
    }
}
