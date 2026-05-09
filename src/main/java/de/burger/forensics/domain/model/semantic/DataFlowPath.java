package de.burger.forensics.domain.model.semantic;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral data flow path between semantic nodes.
 */
public record DataFlowPath(String pathId, String sourceNodeId, String targetNodeId, List<DataFlowStep> steps) {

    public DataFlowPath {
        requireText(pathId, "Path id");
        requireText(sourceNodeId, "Source node id");
        requireText(targetNodeId, "Target node id");
        steps = List.copyOf(Objects.requireNonNull(steps, "Steps must not be null."));
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }
}
