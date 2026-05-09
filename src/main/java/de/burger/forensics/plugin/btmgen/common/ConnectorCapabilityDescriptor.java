package de.burger.forensics.plugin.btmgen.common;

import java.util.Objects;

/**
 * Declarative metadata for one mandatory connector capability.
 */
public record ConnectorCapabilityDescriptor(
        ConnectorCapability capability,
        String description
) {
    public ConnectorCapabilityDescriptor {
        Objects.requireNonNull(capability, "capability");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Capability description must not be blank.");
        }
    }
}
