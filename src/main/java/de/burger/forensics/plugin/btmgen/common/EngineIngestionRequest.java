package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EngineIngestionRequest(
        String schemaVersion,
        String projectId,
        String repositoryUrl,
        String branchName,
        String commitHash,
        String buildId,
        Instant scanTimestamp,
        String moduleName,
        String modulePath,
        String pluginName,
        String pluginVersion,
        List<EngineIngestionPayload> payloads
) {
    private static final String UNKNOWN = "UNKNOWN";
    private static final String PLUGIN_NAME = "forensics-tracing";

    public EngineIngestionRequest {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        projectId = requireText(projectId, "projectId");
        repositoryUrl = requireText(repositoryUrl, "repositoryUrl");
        branchName = requireText(branchName, "branchName");
        commitHash = requireText(commitHash, "commitHash");
        buildId = requireText(buildId, "buildId");
        scanTimestamp = Objects.requireNonNull(scanTimestamp, "scanTimestamp");
        moduleName = requireText(moduleName, "moduleName");
        modulePath = requireText(modulePath, "modulePath");
        pluginName = requireText(pluginName, "pluginName");
        pluginVersion = requireText(pluginVersion, "pluginVersion");
        payloads = List.copyOf(Objects.requireNonNull(payloads, "payloads"));
        if (payloads.isEmpty()) {
            throw new IllegalArgumentException("payloads must not be empty");
        }
    }

    static EngineIngestionRequest from(BtmGenerationRequest request) {
        return new EngineIngestionRequest(
                AnalysisSchemaVersion.CURRENT.value(),
                nonBlankOrUnknown(request.projectKey()),
                UNKNOWN,
                UNKNOWN,
                UNKNOWN,
                UNKNOWN,
                Instant.EPOCH,
                nonBlankOrUnknown(request.moduleName()),
                nonBlankOrUnknown(request.modulePath()),
                PLUGIN_NAME,
                nonBlankOrUnknown(request.pluginVersion()),
                payloads(request)
        );
    }

    private static List<EngineIngestionPayload> payloads(BtmGenerationRequest request) {
        if (request.analysisStoreEnabled()) {
            return List.of(
                    bytemanRules(request),
                    manifest(request),
                    checksums(request)
            );
        }
        return List.of(bytemanRules(request));
    }

    private static EngineIngestionPayload bytemanRules(BtmGenerationRequest request) {
        return new EngineIngestionPayload(
                "byteman-rules",
                EnginePayloadKind.RULE_ARTIFACTS,
                "text/x-byteman",
                request.outputFile(),
                java.util.Map.of("artifact", "btm-rules")
        );
    }

    private static EngineIngestionPayload manifest(BtmGenerationRequest request) {
        return new EngineIngestionPayload(
                "analysis-manifest",
                EnginePayloadKind.DIAGNOSTIC_REPORT,
                "application/json",
                request.manifestFile(),
                java.util.Map.of("artifact", "analysis-manifest")
        );
    }

    private static EngineIngestionPayload checksums(BtmGenerationRequest request) {
        return new EngineIngestionPayload(
                "analysis-checksums",
                EnginePayloadKind.DIAGNOSTIC_REPORT,
                "text/plain",
                request.checksumsFile(),
                java.util.Map.of("artifact", "analysis-checksums")
        );
    }

    private static String nonBlankOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
