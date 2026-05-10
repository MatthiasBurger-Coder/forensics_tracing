package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineIngestionRequestWriterTest {

    @Test
    void writesDeterministicEngineRequestJson(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("build/forensics/engine-request.json");
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("zeta", "last");
        attributes.put("alpha", "first");
        EngineIngestionRequest request = new EngineIngestionRequest(
                "1",
                "project-a",
                "UNKNOWN",
                "main",
                "abcdef",
                "build-1",
                Instant.EPOCH,
                "module-a",
                ":module-a",
                "forensics-tracing",
                "1.2.3",
                List.of(new EngineIngestionPayload(
                        "payload-1",
                        EnginePayloadKind.RULE_ARTIFACTS,
                        "text/x-byteman",
                        tempDir.resolve("build/forensics/rules.btm"),
                        attributes))
        );

        new EngineIngestionRequestWriter().write(target, request);

        String json = Files.readString(target);
        assertThat(json).contains("\"schemaVersion\": \"1\"");
        assertThat(json).contains("\"projectId\": \"project-a\"");
        assertThat(json).contains("\"scanTimestamp\": \"1970-01-01T00:00:00Z\"");
        assertThat(json).contains("\"moduleName\": \"module-a\"");
        assertThat(json).contains("\"pluginName\": \"forensics-tracing\"");
        assertThat(json).contains("\"kind\": \"RULE_ARTIFACTS\"");
        assertThat(json).contains("\"file\": \"" + tempDir.toAbsolutePath().toString().replace('\\', '/') + "/build/forensics/rules.btm\"");
        assertThat(json.indexOf("\"alpha\"")).isLessThan(json.indexOf("\"zeta\""));
    }

    @Test
    void buildsRequestFromGenerationRequestWithoutEnablingNetworkUpload(@TempDir Path tempDir) {
        BtmGenerationRequest generationRequest = BtmGenerationRequest.builder()
                .sourceRoot(tempDir.resolve("src/main/java"))
                .outputFile(tempDir.resolve("build/forensics/rules.btm"))
                .manifestFile(tempDir.resolve("build/forensics/manifest.json"))
                .checksumsFile(tempDir.resolve("build/forensics/checksums.sha256"))
                .analysisStoreEnabled(true)
                .projectKey("project-a")
                .pluginVersion("1.2.3")
                .moduleName("module-a")
                .modulePath(":module-a")
                .build();

        EngineIngestionRequest request = EngineIngestionRequest.from(generationRequest);

        assertThat(request.schemaVersion()).isEqualTo("1");
        assertThat(request.projectId()).isEqualTo("project-a");
        assertThat(request.repositoryUrl()).isEqualTo("UNKNOWN");
        assertThat(request.moduleName()).isEqualTo("module-a");
        assertThat(request.modulePath()).isEqualTo(":module-a");
        assertThat(request.pluginVersion()).isEqualTo("1.2.3");
        assertThat(request.payloads())
                .extracting(EngineIngestionPayload::payloadId)
                .containsExactly("byteman-rules", "analysis-manifest", "analysis-checksums");
        assertThat(new EngineIngestionRequestWriter().toJson(request))
                .contains("\"payloadId\": \"analysis-manifest\"")
                .contains("\"payloadId\": \"analysis-checksums\"");
    }

    @Test
    void escapesJsonFieldsAndWritesEmptyAttributes(@TempDir Path tempDir) {
        EngineIngestionRequest request = new EngineIngestionRequest(
                "1",
                "project\\id",
                "repo",
                "feature\"x",
                "commit\nhash",
                "build\rid",
                Instant.EPOCH,
                "module\tname",
                ":module-a",
                "forensics-tracing",
                "1.2.3",
                List.of(new EngineIngestionPayload(
                        "diagnostic-report",
                        EnginePayloadKind.DIAGNOSTIC_REPORT,
                        "application/json",
                        tempDir.resolve("report.json"),
                        Map.of()))
        );

        String json = new EngineIngestionRequestWriter().toJson(request);

        assertThat(json).contains("\"projectId\": \"project\\\\id\"");
        assertThat(json).contains("\"branchName\": \"feature\\\"x\"");
        assertThat(json).contains("\"commitHash\": \"commit\\nhash\"");
        assertThat(json).contains("\"buildId\": \"build\\rid\"");
        assertThat(json).contains("\"moduleName\": \"module\\tname\"");
        assertThat(json).contains("\"attributes\": {}");
    }

    @Test
    void writesFailuresAsUncheckedIoExceptions(@TempDir Path tempDir) {
        EngineIngestionRequest request = new EngineIngestionRequest(
                "1",
                "project-a",
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                Instant.EPOCH,
                "module-a",
                ":module-a",
                "forensics-tracing",
                "1.2.3",
                List.of(new EngineIngestionPayload(
                        "payload-1",
                        EnginePayloadKind.RULE_ARTIFACTS,
                        "text/x-byteman",
                        tempDir.resolve("rules.btm"),
                        Map.of()))
        );

        assertThatThrownBy(() -> new EngineIngestionRequestWriter().write(tempDir, request))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to write engine ingestion request");
    }

    @Test
    void usesUnknownIdentityForBlankGenerationRequestValues(@TempDir Path tempDir) {
        BtmGenerationRequest generationRequest = BtmGenerationRequest.builder()
                .sourceRoot(tempDir.resolve("src/main/java"))
                .outputFile(tempDir.resolve("build/forensics/rules.btm"))
                .projectKey(" ")
                .pluginVersion("\t")
                .moduleName("")
                .modulePath(" ")
                .build();

        EngineIngestionRequest request = EngineIngestionRequest.from(generationRequest);

        assertThat(request.projectId()).isEqualTo("UNKNOWN");
        assertThat(request.moduleName()).isEqualTo("UNKNOWN");
        assertThat(request.modulePath()).isEqualTo("UNKNOWN");
        assertThat(request.pluginVersion()).isEqualTo("UNKNOWN");
        assertThat(request.payloads())
                .extracting(EngineIngestionPayload::payloadId)
                .containsExactly("byteman-rules");
    }

    @Test
    void rejectsIncompletePayloadsAndRequests(@TempDir Path tempDir) {
        assertThatThrownBy(() -> new EngineIngestionPayload(
                " ",
                EnginePayloadKind.RULE_ARTIFACTS,
                "text/x-byteman",
                tempDir.resolve("rules.btm"),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payloadId");

        assertThatThrownBy(() -> new EngineIngestionRequest(
                "1",
                "project-a",
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                Instant.EPOCH,
                "module-a",
                ":module-a",
                "forensics-tracing",
                "1.2.3",
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payloads");

        assertThatThrownBy(() -> new EngineIngestionRequest(
                " ",
                "project-a",
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                Instant.EPOCH,
                "module-a",
                ":module-a",
                "forensics-tracing",
                "1.2.3",
                List.of(new EngineIngestionPayload(
                        "payload-1",
                        EnginePayloadKind.RULE_ARTIFACTS,
                        "text/x-byteman",
                        tempDir.resolve("rules.btm"),
                        Map.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }
}
