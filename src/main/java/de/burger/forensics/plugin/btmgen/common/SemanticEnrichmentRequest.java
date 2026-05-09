package de.burger.forensics.plugin.btmgen.common;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Build-tool-neutral input model for optional Joern semantic enrichment.
 */
public record SemanticEnrichmentRequest(
        List<Path> sourceRoots,
        Path joernExecutable,
        Path joernParseExecutable,
        Path joernSliceExecutable,
        Path joernWorkspaceDirectory,
        Path joernOutputDirectory,
        int joernTimeoutSeconds,
        boolean joernFailOnError,
        Path analysisStoreDirectory,
        Path manifestFile,
        Path checksumsFile,
        Path outputFile
) {
    public SemanticEnrichmentRequest {
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        Objects.requireNonNull(joernExecutable, "joernExecutable");
        Objects.requireNonNull(joernParseExecutable, "joernParseExecutable");
        Objects.requireNonNull(joernSliceExecutable, "joernSliceExecutable");
        Objects.requireNonNull(joernWorkspaceDirectory, "joernWorkspaceDirectory");
        Objects.requireNonNull(joernOutputDirectory, "joernOutputDirectory");
        if (joernTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("joernTimeoutSeconds must be greater than zero");
        }
        Objects.requireNonNull(analysisStoreDirectory, "analysisStoreDirectory");
        Objects.requireNonNull(manifestFile, "manifestFile");
        Objects.requireNonNull(checksumsFile, "checksumsFile");
        Objects.requireNonNull(outputFile, "outputFile");
    }
}
