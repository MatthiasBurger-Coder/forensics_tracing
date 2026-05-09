package de.burger.forensics.plugin.btmgen.common;

import java.util.List;
import java.util.Objects;

/**
 * Describes current connector support for one forensic capability.
 */
public record ConnectorCapabilityDescriptor(
        ConnectorCapability capability,
        boolean gradleSupported,
        boolean mavenSupported,
        String notes
) {

    public ConnectorCapabilityDescriptor {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(notes, "notes");
        if (notes.isBlank()) {
            throw new IllegalArgumentException("notes must not be blank");
        }
    }

    public boolean hasParity() {
        return gradleSupported == mavenSupported;
    }

    public List<String> missingConnectors() {
        if (gradleSupported && mavenSupported) {
            return List.of();
        }
        if (!gradleSupported && !mavenSupported) {
            return List.of("Gradle", "Maven");
        }
        return gradleSupported ? List.of("Maven") : List.of("Gradle");
    }
}
