package de.burger.forensics.domain.model.semantic;

import de.burger.forensics.domain.model.analysis.BuildIdentity;

import java.util.List;
import java.util.Objects;

/**
 * Input for an external semantic source analysis provider.
 */
public record SemanticAnalysisRequest(BuildIdentity identity,
                                      List<String> sourceRoots,
                                      String workspaceDirectory,
                                      String outputDirectory) {

    public SemanticAnalysisRequest {
        Objects.requireNonNull(identity, "Build identity must not be null.");
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "Source roots must not be null."));
        if (sourceRoots.isEmpty() || sourceRoots.stream().anyMatch(SemanticAnalysisRequest::isBlank)) {
            throw new IllegalArgumentException("Source roots must contain at least one non-blank entry.");
        }
        if (isBlank(workspaceDirectory)) {
            throw new IllegalArgumentException("Workspace directory must not be blank.");
        }
        if (isBlank(outputDirectory)) {
            throw new IllegalArgumentException("Output directory must not be blank.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
