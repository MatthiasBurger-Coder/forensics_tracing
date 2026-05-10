package de.burger.forensics.plugin.btmgen.common;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Build-tool-neutral request for optional Joern semantic enrichment.
 */
public record ForensicsSemanticAnalysisRequest(
        boolean joernEnabled,
        Path joernExecutable,
        Path joernParseExecutable,
        Path joernSliceExecutable,
        Path joernWorkspaceDirectory,
        Path joernOutputDirectory,
        String joernMaxHeap,
        int joernTimeoutSeconds,
        boolean joernFailOnError,
        List<Path> sourceRoots,
        Path analysisStoreDirectory,
        Path manifestFile,
        Path checksumsFile,
        Path outputFile
) {

    public ForensicsSemanticAnalysisRequest {
        Objects.requireNonNull(joernExecutable, "joernExecutable");
        Objects.requireNonNull(joernParseExecutable, "joernParseExecutable");
        Objects.requireNonNull(joernSliceExecutable, "joernSliceExecutable");
        Objects.requireNonNull(joernWorkspaceDirectory, "joernWorkspaceDirectory");
        Objects.requireNonNull(joernOutputDirectory, "joernOutputDirectory");
        Objects.requireNonNull(joernMaxHeap, "joernMaxHeap");
        if (joernTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("joernTimeoutSeconds must be positive");
        }
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        Objects.requireNonNull(analysisStoreDirectory, "analysisStoreDirectory");
        Objects.requireNonNull(manifestFile, "manifestFile");
        Objects.requireNonNull(checksumsFile, "checksumsFile");
        Objects.requireNonNull(outputFile, "outputFile");
    }
}
