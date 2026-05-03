package de.burger.forensics.adaptersupport.javaparser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserResolutionGuardTest {

    @Test
    void returnsResolvedValue() {
        assertThat(JavaParserResolutionGuard.resolve(() -> "resolved")).contains("resolved");
    }

    @Test
    void returnsEmptyForRuntimeResolutionFailure() {
        assertThat(JavaParserResolutionGuard.resolve(() -> {
            throw new IllegalStateException("unresolved");
        })).isEmpty();
    }

    @Test
    void returnsEmptyForRecursiveSolverFailure() {
        assertThat(JavaParserResolutionGuard.resolve(() -> {
            throw new StackOverflowError("recursive symbol resolution");
        })).isEmpty();
    }
}
