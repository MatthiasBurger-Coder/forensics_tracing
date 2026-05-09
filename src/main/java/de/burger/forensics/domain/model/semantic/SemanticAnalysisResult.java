package de.burger.forensics.domain.model.semantic;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral semantic analysis result.
 */
public record SemanticAnalysisResult(String providerVersion,
                                     String semanticFingerprint,
                                     List<ArtifactChecksum> artifacts,
                                     List<SemanticNode> nodes,
                                     List<SemanticEdge> edges,
                                     List<SemanticMethod> methods,
                                     List<CallRelation> callRelations,
                                     List<ControlFlowRelation> controlFlowRelations,
                                     List<DataFlowPath> dataFlowPaths,
                                     List<SemanticAnchor> anchors) {

    public SemanticAnalysisResult {
        if (isBlank(providerVersion)) {
            throw new IllegalArgumentException("Provider version must not be blank.");
        }
        if (isBlank(semanticFingerprint)) {
            throw new IllegalArgumentException("Semantic fingerprint must not be blank.");
        }
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "Artifacts must not be null."));
        nodes = List.copyOf(Objects.requireNonNull(nodes, "Nodes must not be null."));
        edges = List.copyOf(Objects.requireNonNull(edges, "Edges must not be null."));
        methods = List.copyOf(Objects.requireNonNull(methods, "Methods must not be null."));
        callRelations = List.copyOf(Objects.requireNonNull(callRelations, "Call relations must not be null."));
        controlFlowRelations = List.copyOf(Objects.requireNonNull(
                controlFlowRelations,
                "Control flow relations must not be null."));
        dataFlowPaths = List.copyOf(Objects.requireNonNull(dataFlowPaths, "Data flow paths must not be null."));
        anchors = List.copyOf(Objects.requireNonNull(anchors, "Anchors must not be null."));
    }

    public static SemanticAnalysisResult empty(String providerVersion, String semanticFingerprint) {
        return new SemanticAnalysisResult(
                providerVersion,
                semanticFingerprint,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
