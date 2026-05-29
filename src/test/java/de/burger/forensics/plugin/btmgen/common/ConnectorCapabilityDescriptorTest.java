package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorCapabilityDescriptorTest {

    @Test
    void reportsNoMissingConnectorsWhenBothBuildToolsSupportCapability() {
        ConnectorCapabilityDescriptor descriptor = new ConnectorCapabilityDescriptor(
                ConnectorCapability.BTM_GENERATION,
                true,
                true,
                "supported");

        assertThat(descriptor.hasParity()).isTrue();
        assertThat(descriptor.missingConnectors()).isEmpty();
    }

    @Test
    void reportsBothConnectorsWhenNeitherBuildToolSupportsCapability() {
        ConnectorCapabilityDescriptor descriptor = new ConnectorCapabilityDescriptor(
                ConnectorCapability.JOERN_CONFIGURATION,
                false,
                false,
                "not supported");

        assertThat(descriptor.hasParity()).isTrue();
        assertThat(descriptor.missingConnectors()).containsExactly("Gradle", "Maven");
    }

    @Test
    void reportsMavenWhenOnlyGradleSupportsCapability() {
        ConnectorCapabilityDescriptor descriptor = new ConnectorCapabilityDescriptor(
                ConnectorCapability.JOERN_IMPORT,
                true,
                false,
                "gradle only");

        assertThat(descriptor.hasParity()).isFalse();
        assertThat(descriptor.missingConnectors()).containsExactly("Maven");
    }

    @Test
    void reportsGradleWhenOnlyMavenSupportsCapability() {
        ConnectorCapabilityDescriptor descriptor = new ConnectorCapabilityDescriptor(
                ConnectorCapability.JOERN_IMPORT,
                false,
                true,
                "maven only");

        assertThat(descriptor.hasParity()).isFalse();
        assertThat(descriptor.missingConnectors()).containsExactly("Gradle");
    }

    @Test
    void rejectsBlankNotes() {
        assertThatThrownBy(() -> new ConnectorCapabilityDescriptor(
                ConnectorCapability.BTM_GENERATION,
                true,
                true,
                " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notes");
    }

    @Test
    void rejectsNullCapabilityAndNotes() {
        assertThatThrownBy(() -> new ConnectorCapabilityDescriptor(null, true, true, "supported"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("capability");
        assertThatThrownBy(() -> new ConnectorCapabilityDescriptor(
                ConnectorCapability.BTM_GENERATION,
                true,
                true,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("notes");
    }

    @Test
    void catalogReportsParityForAllDeclaredCapabilities() {
        assertThat(ConnectorCapabilityCatalog.descriptors())
                .hasSize(ConnectorCapability.values().length)
                .extracting(ConnectorCapabilityDescriptor::hasParity)
                .containsOnly(true);
        assertThat(ConnectorCapabilityCatalog.parityGaps()).isEmpty();
    }
}
