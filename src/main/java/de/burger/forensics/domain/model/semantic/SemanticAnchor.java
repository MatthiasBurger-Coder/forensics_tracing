package de.burger.forensics.domain.model.semantic;

/**
 * Correlates a scan event or generated rule with a semantic graph node.
 */
public record SemanticAnchor(String scanEventKey,
                             String semanticNodeId,
                             String relativePath,
                             String fqcn,
                             String methodName,
                             String signature,
                             int lineNumber,
                             String normalizedCode,
                             double confidence,
                             String matchStrategy) {

    public SemanticAnchor {
        requireText(scanEventKey, "Scan event key");
        requireText(semanticNodeId, "Semantic node id");
        requireText(relativePath, "Relative path");
        requireText(methodName, "Method name");
        requireText(matchStrategy, "Match strategy");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("Line number must be positive.");
        }
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0.");
        }
        normalizedCode = normalizedCode == null ? "" : normalizedCode;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }
}
