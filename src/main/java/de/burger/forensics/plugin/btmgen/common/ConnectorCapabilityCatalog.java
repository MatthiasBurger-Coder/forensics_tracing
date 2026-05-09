package de.burger.forensics.plugin.btmgen.common;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative capability inventory for supported build-tool connectors.
 */
public final class ConnectorCapabilityCatalog {

    public enum Connector {
        GRADLE,
        MAVEN
    }

    private static final List<ConnectorCapabilityDescriptor> CAPABILITIES = List.of(
            descriptor(ConnectorCapability.BTM_GENERATION, "Generate deterministic Byteman rules through the shared runner."),
            descriptor(ConnectorCapability.SINGLE_PROJECT_SCAN, "Scan one project or module source root set."),
            descriptor(ConnectorCapability.MULTI_MODULE_AGGREGATION, "Aggregate Gradle subprojects or Maven reactor projects."),
            descriptor(ConnectorCapability.EXPLICIT_SOURCE_ROOTS, "Accept explicitly configured source roots."),
            descriptor(ConnectorCapability.MAIN_SOURCE_ROOTS, "Discover main Java source roots from the build tool model."),
            descriptor(ConnectorCapability.TEST_SOURCE_ROOTS, "Include test Java source roots when requested."),
            descriptor(ConnectorCapability.INCLUDE_FILTERS, "Apply include package or class prefixes consistently."),
            descriptor(ConnectorCapability.EXCLUDE_FILTERS, "Apply exclude package or class prefixes consistently."),
            descriptor(ConnectorCapability.STRICT_PARSING, "Fail consistently on parser errors when strict parsing is enabled."),
            descriptor(ConnectorCapability.STRICT_CONDITION_VALIDATION, "Use the same strict condition validation behavior."),
            descriptor(ConnectorCapability.SCAN_CACHE, "Use the shared H2 parser scan cache model."),
            descriptor(ConnectorCapability.DEPENDENCY_AWARE_INVALIDATION, "Invalidate parser scan cache entries conservatively when dependency-aware invalidation is requested."),
            descriptor(ConnectorCapability.PROFILING, "Write the shared parser scan profile format."),
            descriptor(ConnectorCapability.INCLUDE_ENTRY_EXIT, "Control generated method entry and exit rules."),
            descriptor(ConnectorCapability.MIN_BRANCHES_PER_METHOD, "Filter generated rules by minimum branch count."),
            descriptor(ConnectorCapability.TIMESTAMP_HEADER, "Control deterministic timestamp header behavior."),
            descriptor(ConnectorCapability.ANALYSIS_STORE, "Write the shared local Analysis Store package."),
            descriptor(ConnectorCapability.CLEANUP_POLICY, "Apply shared Analysis Store cleanup policies."),
            descriptor(ConnectorCapability.MANIFEST, "Write the shared analysis manifest schema."),
            descriptor(ConnectorCapability.CHECKSUMS, "Write the shared checksum artifact format."),
            descriptor(ConnectorCapability.BUILD_IDENTITY, "Use shared BuildIdentity and AnalysisRunId semantics."),
            descriptor(ConnectorCapability.JOERN_CONFIGURATION, "Expose optional Joern executable and timeout configuration."),
            descriptor(ConnectorCapability.JOERN_SEMANTIC_ANALYSIS, "Run semantic enrichment through the shared use case."),
            descriptor(ConnectorCapability.JOERN_IMPORT, "Import semantic artifacts into the shared Analysis Store schema."),
            descriptor(ConnectorCapability.FULL_ANALYSIS_AGGREGATE, "Expose one full analysis aggregate command or goal."),
            descriptor(ConnectorCapability.CLEAN_ANALYSIS_ARTIFACTS, "Clean generated analysis artifacts through the connector.")
    );

    private static final Map<Connector, Set<ConnectorCapability>> CONNECTOR_CAPABILITIES = connectorCapabilities();

    private ConnectorCapabilityCatalog() {
    }

    public static List<ConnectorCapabilityDescriptor> descriptors() {
        return CAPABILITIES;
    }

    public static Set<ConnectorCapability> mandatoryCapabilities() {
        return EnumSet.copyOf(CAPABILITIES.stream()
                .map(ConnectorCapabilityDescriptor::capability)
                .toList());
    }

    public static Set<ConnectorCapability> capabilitiesFor(Connector connector) {
        return EnumSet.copyOf(CONNECTOR_CAPABILITIES.get(connector));
    }

    private static ConnectorCapabilityDescriptor descriptor(ConnectorCapability capability, String description) {
        return new ConnectorCapabilityDescriptor(capability, description);
    }

    private static Map<Connector, Set<ConnectorCapability>> connectorCapabilities() {
        EnumSet<ConnectorCapability> mandatory = EnumSet.copyOf(mandatoryCapabilities());
        EnumMap<Connector, Set<ConnectorCapability>> capabilities = new EnumMap<>(Connector.class);
        capabilities.put(Connector.GRADLE, EnumSet.copyOf(mandatory));
        capabilities.put(Connector.MAVEN, EnumSet.copyOf(mandatory));
        return Map.copyOf(capabilities);
    }
}
