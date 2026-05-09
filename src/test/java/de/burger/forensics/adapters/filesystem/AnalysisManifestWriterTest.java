package de.burger.forensics.adapters.filesystem;

import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisManifestWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesManifestJsonWithEscapedValues() throws IOException {
        Path manifest = tempDir.resolve("manifest.json");
        BuildIdentity identity = new BuildIdentity(
                "demo\"project\\with\b\f\n\r\tcontrols",
                new AnalysisRunId("run-1"),
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                BuildIdentity.NOT_COMPUTED,
                "sha256:rules",
                BuildIdentity.NOT_COMPUTED,
                "test",
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);

        new AnalysisManifestWriter().write(manifest, identity, List.of(
                new ArtifactChecksum("forensics.btm", "byteman-rules", "abc", 12L)));

        String json = Files.readString(manifest);
        assertThat(json).contains(
                "\"schemaVersion\": \"1\"",
                "\"projectKey\": \"demo\\\"project\\\\with\\b\\f\\n\\r\\tcontrols\"",
                "\"analysisRunId\": \"run-1\"",
                "\"joernEnabled\": false",
                "\"path\": \"forensics.btm\"");
    }

    @Test
    void writesJoernManifestSectionWhenSemanticResultIsPresent() throws IOException {
        Path manifest = tempDir.resolve("manifest-with-joern.json");
        SemanticAnalysisResult semanticResult = SemanticAnalysisResult.empty("joern 1.0", "sha256:semantic");

        new AnalysisManifestWriter().write(
                manifest,
                identity(),
                List.of(new ArtifactChecksum("forensics.btm", "byteman-rules", "abc", 12L)),
                semanticResult);

        String json = Files.readString(manifest);
        assertThat(json).contains("\"joernEnabled\": true");
        assertThat(json).contains("\"joernVersion\": \"joern 1.0\"");
        assertThat(json).contains("\"joernFingerprint\": \"sha256:semantic\"");
        assertThat(json).contains("\"joernArtifacts\": [");
    }

    @Test
    void wrapsManifestWriteFailures() {
        Path manifest = tempDir.resolve("manifest-dir");

        assertThatCode(() -> Files.createDirectories(manifest))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> new AnalysisManifestWriter().write(manifest, identity(), List.of()))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("Failed to write analysis manifest");
    }

    private static BuildIdentity identity() {
        return new BuildIdentity(
                "demo",
                new AnalysisRunId("run-1"),
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                BuildIdentity.NOT_COMPUTED,
                "sha256:rules",
                BuildIdentity.NOT_COMPUTED,
                "test",
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);
    }
}
