package de.burger.forensics.plugin.btmgen.grpc;

public record ForensicsSubmissionResult(
        String sessionId,
        String status,
        String message,
        long uploadedPayloads
) {
}
