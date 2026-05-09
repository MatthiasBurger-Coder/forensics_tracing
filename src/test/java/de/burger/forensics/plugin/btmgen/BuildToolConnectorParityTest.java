package de.burger.forensics.plugin.btmgen;

import de.burger.forensics.plugin.btmgen.common.ConnectorCapability;
import de.burger.forensics.plugin.btmgen.common.ConnectorCapabilityCatalog;
import de.burger.forensics.plugin.btmgen.common.ConnectorCapabilityDescriptor;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildToolConnectorParityTest {

    @Test
    void catalogListsEveryCapabilityExactlyOnce() {
        Set<ConnectorCapability> listed = EnumSet.noneOf(ConnectorCapability.class);

        for (ConnectorCapabilityDescriptor descriptor : ConnectorCapabilityCatalog.descriptors()) {
            assertThat(descriptor.description()).isNotBlank();
            assertThat(listed.add(descriptor.capability()))
                    .as("duplicate capability " + descriptor.capability())
                    .isTrue();
        }

        assertThat(listed).containsExactlyInAnyOrder(ConnectorCapability.values());
    }

    @Test
    void gradleAndMavenExposeTheSameMandatoryCapabilities() {
        Set<ConnectorCapability> mandatory = ConnectorCapabilityCatalog.mandatoryCapabilities();

        assertThat(ConnectorCapabilityCatalog.capabilitiesFor(ConnectorCapabilityCatalog.Connector.GRADLE))
                .containsExactlyInAnyOrderElementsOf(mandatory);
        assertThat(ConnectorCapabilityCatalog.capabilitiesFor(ConnectorCapabilityCatalog.Connector.MAVEN))
                .containsExactlyInAnyOrderElementsOf(mandatory);
    }

    @Test
    void descriptorRequiresCapability() {
        assertThatThrownBy(() -> new ConnectorCapabilityDescriptor(null, "BTM generation"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("capability");
    }

    @Test
    void descriptorRequiresDescription() {
        assertThatThrownBy(() -> new ConnectorCapabilityDescriptor(ConnectorCapability.BTM_GENERATION, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capability description must not be blank.");
    }
}
