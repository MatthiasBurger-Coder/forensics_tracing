package de.burger.forensics.plugin.translate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnsafeExprTranslatorTest {

    private static final String H = "org.example.trace.SafeEval";

    @Test
    void equalsToHelper() {
        String helper = UnsafeExprTranslator.toHelperExpr("user.status == \"OK\"");
        assertThat(helper).isEqualTo(H + ".ifEq(user.status, \"OK\")");
    }

    @Test
    void notEqualsNegatesHelper() {
        String helper = UnsafeExprTranslator.toHelperExpr("code != 200");
        assertThat(helper).isEqualTo("!" + H + ".ifEq(code, 200)");
    }

    @Test
    void instanceofBecomesHelperCall() {
        String helper = UnsafeExprTranslator.toHelperExpr("obj instanceof com.acme.Foo");
        assertThat(helper).isEqualTo(H + ".ifInstanceOf(obj, \"com.acme.Foo\")");
    }

    @Test
    void booleanCompositionUsesAndOr() {
        String helper = UnsafeExprTranslator.toHelperExpr("(a == 1) && (b != 2) || (c instanceof X)");
        assertThat(helper)
            .contains(H + ".and(")
            .contains(H + ".or(")
            .contains(H + ".ifEq(a, 1)")
            .contains("!" + H + ".ifEq(b, 2)")
            .contains(H + ".ifInstanceOf(c, \"X\")");
    }

    @Test
    void unsupportedExpressionFallsBackToTrue() {
        String helper = UnsafeExprTranslator.toHelperExpr("x != null && x.equals(\"OK\")");
        assertThat(helper).isEqualTo("true");
    }

    @Test
    void standaloneUnsafeIdentifierFallsBackToTrue() {
        String helper = UnsafeExprTranslator.toHelperExpr("flag");
        assertThat(helper).isEqualTo("true");
    }
}
