package de.burger.forensics.domain.model.semantic;

/**
 * Provider-neutral code graph node.
 */
public record SemanticNode(String nodeId,
                           String nodeType,
                           String relativePath,
                           String fqcn,
                           String methodName,
                           String signature,
                           int lineNumber,
                           String normalizedCode) {

    public SemanticNode {
        requireText(nodeId, "Node id");
        requireText(nodeType, "Node type");
        requireText(relativePath, "Relative path");
        requireText(methodName, "Method name");
        requireLine(lineNumber);
        normalizedCode = normalizedCode == null ? "" : normalizedCode;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }

    private static void requireLine(int lineNumber) {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be positive.");
        }
    }
}
