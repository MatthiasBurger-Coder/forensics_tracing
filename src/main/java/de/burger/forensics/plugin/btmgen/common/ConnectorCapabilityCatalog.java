package de.burger.forensics.plugin.btmgen.common;

import java.util.List;

/**
 * Legacy build-tool connector capability inventory retained for migration verification.
 * Active Gradle and Maven entry points submit build context over gRPC instead.
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
            parity(ConnectorCapability.ENGINE_REQUEST, "Both connectors can build an opt-in engine ingestion request artifact."),
            parity(ConnectorCapability.JOERN_CONFIGURATION, "Both connectors expose Joern configuration with Joern disabled by default."),
            parity(ConnectorCapability.JOERN_SEMANTIC_ANALYSIS, "Both connectors delegate semantic analysis to ForensicsSemanticAnalysisRunner."),
            parity(ConnectorCapability.JOERN_IMPORT, "Both connectors verify Joern semantic artifacts through the shared verifier."),
            parity(ConnectorCapability.FULL_ANALYSIS_AGGREGATE, "Gradle forensicsAnalyze and Maven analyze/analyze-aggregate run full analysis."),
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
}
