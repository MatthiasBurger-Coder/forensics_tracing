package de.burger.forensics.domain.model.semantic;

/**
 * Provider-neutral call relation between semantic methods.
 */
public record CallRelation(String callerMethodId, String calleeMethodId, String callNodeId) {

    public CallRelation {
        requireText(callerMethodId, "Caller method id");
        requireText(calleeMethodId, "Callee method id");
        requireText(callNodeId, "Call node id");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
    }
}
