package de.burger.forensics.plugin.btmgen;

import de.burger.forensics.plugin.btmgen.common.ConnectorCapability;
import de.burger.forensics.plugin.btmgen.common.ConnectorCapabilityCatalog;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class BuildToolConnectorParityTest {

    @Test
    void catalogDescribesEveryKnownCapabilityExactlyOnce() {
        assertThat(ConnectorCapabilityCatalog.descriptors())
                .extracting(descriptor -> descriptor.capability())
                .containsExactlyInAnyOrder(ConnectorCapability.values());
    }

    @Test
    void currentParityGapsAreExplicitlyTracked() {
        assertThat(ConnectorCapabilityCatalog.parityGaps())
                .allSatisfy(descriptor -> {
                    assertThat(descriptor.notes()).isNotBlank();
                    assertThat(descriptor.missingConnectors()).isNotEmpty();
                });
    }

    @Test
    void catalogUsesBuildToolNeutralCapabilityNames() {
        assertThat(Arrays.stream(ConnectorCapability.values()).map(Enum::name))
                .noneMatch(name -> name.contains("GRADLE"))
                .noneMatch(name -> name.contains("MAVEN"));
    }

    @Disabled("Workflow parity slices must remove known Gradle/Maven connector gaps before this assertion is enabled.")
    @Test
    void everyCapabilityIsSupportedByBothBuildToolConnectors() {
        assertThat(ConnectorCapabilityCatalog.parityGaps()).isEmpty();
    }
}
