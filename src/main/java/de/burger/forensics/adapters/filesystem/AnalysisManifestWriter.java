package de.burger.forensics.adapters.filesystem;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.port.out.AnalysisManifestPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes the machine-readable manifest for one analysis package.
 */
public final class AnalysisManifestWriter implements AnalysisManifestPort {

    @Override
    public void write(Path manifestFile, BuildIdentity identity, List<ArtifactChecksum> artifacts) {
        write(manifestFile, identity, artifacts, null);
    }

    public void write(
            Path manifestFile,
            BuildIdentity identity,
            List<ArtifactChecksum> artifacts,
            SemanticAnalysisResult semanticResult
    ) {
        Objects.requireNonNull(manifestFile, "Manifest file must not be null.");
        Objects.requireNonNull(identity, "Build identity must not be null.");
        Objects.requireNonNull(artifacts, "Artifacts must not be null.");
        try {
            Path parent = manifestFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(manifestFile, manifest(identity, artifacts, semanticResult), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write analysis manifest " + manifestFile + ".", e);
        }
    }

    private static String manifest(
            BuildIdentity identity,
            List<ArtifactChecksum> artifacts,
            SemanticAnalysisResult semanticResult
    ) {
        return """
                {
                  "schemaVersion": "%s",
                  "projectKey": "%s",
                  "analysisRunId": "%s",
                  "buildId": "%s",
                  "sourceFingerprint": "%s",
                  "btmRulesFingerprint": "%s",
                  "pluginVersion": "%s",
                  "joernEnabled": %s%s,
                  "createdAt": "%s",
                  "artifacts": [
                %s
                  ]
                }
                """.formatted(
                escape(identity.schemaVersion().value()),
                escape(identity.projectKey()),
                escape(identity.analysisRunId().value()),
                escape(identity.buildId().value()),
                escape(identity.sourceFingerprint().value()),
                escape(identity.btmRulesFingerprint()),
                escape(identity.pluginVersion()),
                semanticResult == null ? "false" : "true",
                semanticJson(semanticResult),
                escape(identity.createdAt().toString()),
                artifactJson(artifacts));
    }

    private static String semanticJson(SemanticAnalysisResult semanticResult) {
        if (semanticResult == null) {
            return "";
        }
        return """
                ,
                  "joernVersion": "%s",
                  "joernFingerprint": "%s",
                  "joernArtifacts": [
                %s
                  ]""".formatted(
                escape(semanticResult.providerVersion()),
                escape(semanticResult.semanticFingerprint()),
                artifactJson(semanticResult.artifacts()));
    }

    private static String artifactJson(List<ArtifactChecksum> artifacts) {
        return artifacts.stream()
                .map(AnalysisManifestWriter::artifactJson)
                .collect(Collectors.joining(",%n".formatted()));
    }

    private static String artifactJson(ArtifactChecksum artifact) {
        return """
                    {
                      "path": "%s",
                      "type": "%s",
                      "sha256": "%s",
                      "sizeBytes": %d
                    }""".formatted(
                escape(artifact.path()),
                escape(artifact.type()),
                escape(artifact.sha256()),
                artifact.sizeBytes());
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(current);
            }
        }
        return builder.toString();
    }
}
