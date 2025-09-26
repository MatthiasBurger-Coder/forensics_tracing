package org.example.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SafeEvalRegistryTest {

    @AfterEach
    void resetRegistry() {
        SafeEval._clearForTests();
    }

    @Test
    void returnsTrueWhenNoEvaluatorInstalled() {
        assertThat(SafeEval.ifMatch("missing")).isTrue();
    }

    @Test
    void registersAndEvaluatesPredicates() {
        SafeEval.register("RID", () -> true);
        assertThat(SafeEval.ifMatch("RID")).isTrue();
        SafeEval.register("RID", () -> false);
        assertThat(SafeEval.ifMatch("RID")).isFalse();
    }

    @Test
    void evaluatorExceptionsAreFailOpen() {
        SafeEval.register("RID", () -> {
            throw new IllegalStateException("boom");
        });
        assertThat(SafeEval.ifMatch("RID")).isTrue();
    }
}
