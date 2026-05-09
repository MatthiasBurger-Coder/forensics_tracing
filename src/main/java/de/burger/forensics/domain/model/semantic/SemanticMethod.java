package de.burger.forensics.domain.model.semantic;

/**
 * Provider-neutral method entry discovered by semantic analysis.
 */
public record SemanticMethod(String methodId,
                             String relativePath,
                             String fqcn,
                             String methodName,
                             String signature,
                             int lineNumber) {

    public SemanticMethod {
        requireText(methodId, "Method id");
        requireText(relativePath, "Relative path");
        requireText(fqcn, "FQCN");
        requireText(methodName, "Method name");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be positive.");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }
}
