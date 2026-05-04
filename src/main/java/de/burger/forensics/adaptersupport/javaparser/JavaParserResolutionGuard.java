package de.burger.forensics.adaptersupport.javaparser;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Protects optional JavaParser symbol resolution from recursive solver failures.
 */
final class JavaParserResolutionGuard {

    private JavaParserResolutionGuard() {
    }

    static <T> Optional<T> resolve(Supplier<T> resolution) {
        try {
            return Optional.ofNullable(resolution.get());
        } catch (StackOverflowError | RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
