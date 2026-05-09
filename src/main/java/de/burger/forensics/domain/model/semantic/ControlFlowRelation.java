package de.burger.forensics.domain.model.semantic;

/**
 * Provider-neutral control flow relation between semantic nodes.
 */
public record ControlFlowRelation(String sourceNodeId, String targetNodeId, String relationType) {

    public ControlFlowRelation {
        requireText(sourceNodeId, "Source node id");
        requireText(targetNodeId, "Target node id");
        requireText(relationType, "Relation type");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }
}
