package de.burger.forensics.domain.model.semantic;

/**
 * Provider-neutral directed code graph edge.
 */
public record SemanticEdge(String edgeId, String sourceNodeId, String targetNodeId, String edgeType) {

    public SemanticEdge {
        requireText(edgeId, "Edge id");
        requireText(sourceNodeId, "Source node id");
        requireText(targetNodeId, "Target node id");
        requireText(edgeType, "Edge type");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }
}
