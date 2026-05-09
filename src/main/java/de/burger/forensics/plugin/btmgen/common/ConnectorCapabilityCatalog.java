package de.burger.forensics.plugin.btmgen.common;

import java.util.List;

/**
 * Current build-tool connector capability inventory.
 */
public final class ConnectorCapabilityCatalog {

    private static final List<ConnectorCapabilityDescriptor> DESCRIPTORS = List.of(
            parity(ConnectorCapability.BTM_GENERATION, "Both connectors delegate to BtmGenerationRunner."),
            parity(ConnectorCapability.SOURCE_ROOTS, "Both connectors accept explicit source roots."),
            parity(ConnectorCapability.MAIN_SOURCE_ROOTS, "Both connectors discover main Java source roots."),
            parity(ConnectorCapability.TEST_SOURCE_ROOTS, "Both connectors expose includeTests for test source roots."),
            parity(ConnectorCapability.MULTI_MODULE_AGGREGATION, "Gradle supports scanSubprojects and Maven supports reactor aggregation."),
            parity(ConnectorCapability.INCLUDE_FILTERS, "Both connectors map include package/class prefixes."),
            parity(ConnectorCapability.EXCLUDE_FILTERS, "Both connectors map exclude package/class prefixes."),
            parity(ConnectorCapability.STRICT_PARSING, "Both connectors map strictParsing."),
            parity(ConnectorCapability.STRICT_CONDITION_VALIDATION, "Both connectors map strictConditionValidation."),
            parity(ConnectorCapability.DEPENDENCY_AWARE_INVALIDATION, "Both connectors map dependencyAwareInvalidation."),
            parity(ConnectorCapability.SCAN_CACHE, "Both connectors map cache settings."),
            parity(ConnectorCapability.PROFILING, "Both connectors map profiling settings."),
            parity(ConnectorCapability.INCLUDE_ENTRY_EXIT, "Both connectors map includeEntryExit."),
            parity(ConnectorCapability.MIN_BRANCHES_PER_METHOD, "Both connectors map minBranchesPerMethod."),
            parity(ConnectorCapability.INCLUDE_TIMESTAMP_HEADER, "Both connectors map includeTimestampHeader."),
            parity(ConnectorCapability.ANALYSIS_STORE, "Both connectors map Analysis Store settings."),
            parity(ConnectorCapability.CLEANUP_POLICY, "Both connectors map cleanupPolicy."),
            parity(ConnectorCapability.MANIFEST, "Both connectors map manifest output."),
            parity(ConnectorCapability.CHECKSUMS, "Both connectors map checksum output."),
            parity(ConnectorCapability.BUILD_IDENTITY, "Both connectors map project identity into the shared request."),
            mavenGap(ConnectorCapability.JOERN_CONFIGURATION, "Gradle exposes Joern configuration; Maven mapping is pending."),
            mavenGap(ConnectorCapability.JOERN_SEMANTIC_ANALYSIS, "Gradle has analyzeForensicsSemantics; Maven semantic goal is pending."),
            mavenGap(ConnectorCapability.JOERN_IMPORT, "Gradle has importForensicsSemantics; Maven import behavior is pending."),
            mavenGap(ConnectorCapability.FULL_ANALYSIS_AGGREGATE, "Gradle has forensicsAnalyze; Maven full-analysis goal is pending."),
            parity(ConnectorCapability.CLEAN_GENERATED_ANALYSIS_ARTIFACTS, "Gradle and Maven both expose generated analysis artifact cleanup.")
    );

    private ConnectorCapabilityCatalog() {
    }

    public static List<ConnectorCapabilityDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    public static List<ConnectorCapabilityDescriptor> parityGaps() {
        return DESCRIPTORS.stream()
                .filter(descriptor -> !descriptor.hasParity())
                .toList();
    }

    private static ConnectorCapabilityDescriptor parity(ConnectorCapability capability, String notes) {
        return new ConnectorCapabilityDescriptor(capability, true, true, notes);
    }

    private static ConnectorCapabilityDescriptor mavenGap(ConnectorCapability capability, String notes) {
        return new ConnectorCapabilityDescriptor(capability, true, false, notes);
    }

    private static ConnectorCapabilityDescriptor gradleGap(ConnectorCapability capability, String notes) {
        return new ConnectorCapabilityDescriptor(capability, false, true, notes);
    }
}
