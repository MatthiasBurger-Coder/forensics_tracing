package de.burger.forensics.domain.model.semantic;

/**
 * One ordered node in a provider-neutral data flow path.
 */
public record DataFlowStep(String nodeId, int orderIndex, String kind) {

    public DataFlowStep {
        requireText(nodeId, "Node id");
        requireText(kind, "Step kind");
        if (orderIndex < 0) {
            throw new IllegalArgumentException("Order index must not be negative.");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }
}
