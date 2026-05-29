package de.burger.forensics.plugin.btmgen.grpc;

import java.util.List;
import java.util.Objects;

public record ForensicsSubmission(
        String schemaVersion,
        String projectId,
        String repositoryUrl,
        String branchName,
        String commitHash,
        String buildId,
        String scanTimestamp,
        String moduleName,
        String modulePath,
        String pluginName,
        String pluginVersion,
        List<ForensicsPayload> payloads
) {
    public ForensicsSubmission {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        projectId = requireText(projectId, "projectId");
        repositoryUrl = requireText(repositoryUrl, "repositoryUrl");
        branchName = requireText(branchName, "branchName");
        commitHash = requireText(commitHash, "commitHash");
        buildId = requireText(buildId, "buildId");
        scanTimestamp = requireText(scanTimestamp, "scanTimestamp");
        moduleName = requireText(moduleName, "moduleName");
        modulePath = requireText(modulePath, "modulePath");
        pluginName = requireText(pluginName, "pluginName");
        pluginVersion = requireText(pluginVersion, "pluginVersion");
        payloads = List.copyOf(Objects.requireNonNull(payloads, "payloads"));
        if (payloads.isEmpty()) {
            throw new IllegalArgumentException("payloads must not be empty");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
