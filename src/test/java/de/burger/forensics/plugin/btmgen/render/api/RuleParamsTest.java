package de.burger.forensics.plugin.btmgen.render.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleParamsTest {

    @Test
    void canonicalConstructorNormalizesBlankOrNullHelpers() {
        RuleParams blankHelper = new RuleParams(
                "rule-1",
                "com.example.Foo",
                "work",
                "()V",
                "Foo#work",
                null,
                null,
                "  ",
                27,
                "boolean"
        );
        RuleParams nullHelper = new RuleParams(
                "rule-2",
                "com.example.Foo",
                "work",
                "()V",
                "Foo#work",
                null,
                null,
                null,
                28,
                null
        );

        assertThat(blankHelper.helperFqn()).isEqualTo(RuleParams.DEFAULT_HELPER_FQN);
        assertThat(blankHelper.sourceLine()).isEqualTo(27);
        assertThat(blankHelper.returnType()).isEqualTo("boolean");
        assertThat(nullHelper.helperFqn()).isEqualTo(RuleParams.DEFAULT_HELPER_FQN);
        assertThat(nullHelper.sourceLine()).isEqualTo(28);
        assertThat(nullHelper.returnType()).isNull();
    }

    @Test
    void convenienceConstructorKeepsProvidedHelperAndUsesUnknownSourceLine() {
        RuleParams params = new RuleParams(
                "rule-3",
                "com.example.Foo",
                "work",
                "()V",
                "Foo#work",
                null,
                null,
                "com.example.Helper"
        );

        assertThat(params.helperFqn()).isEqualTo("com.example.Helper");
        assertThat(params.sourceLine()).isEqualTo(RuleParams.UNKNOWN_SOURCE_LINE);
    }
}
