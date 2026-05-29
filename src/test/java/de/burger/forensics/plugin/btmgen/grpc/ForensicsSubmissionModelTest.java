package de.burger.forensics.plugin.btmgen.grpc;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForensicsSubmissionModelTest {

    @Test
    void payloadDefensivelyCopiesContentAndSortsAttributes() {
        byte[] content = "payload".getBytes(StandardCharsets.UTF_8);
        ForensicsPayload payload = new ForensicsPayload(
                "payload-1",
                ForensicsPayload.Kind.DIAGNOSTIC_REPORT,
                "application/json",
                content,
                Map.of("zeta", "last", "alpha", "first"));

        content[0] = 'X';
        byte[] returned = payload.content();
        returned[1] = 'X';

        assertThat(new String(payload.content(), StandardCharsets.UTF_8)).isEqualTo("payload");
        assertThat(payload.attributes().keySet()).containsExactly("alpha", "zeta");
    }

    @Test
    void modelsRejectMissingRequiredValues() {
        assertThatThrownBy(() -> new ForensicsPayload(
                "payload-1",
                ForensicsPayload.Kind.DIAGNOSTIC_REPORT,
                "application/json",
                new byte[0],
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content must not be empty");

        assertThatThrownBy(() -> new ForensicsSubmission(
                "1",
                "project-a",
                "repo",
                "main",
                "commit",
                "build",
                "timestamp",
                "module",
                ":module",
                "plugin",
                "version",
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payloads must not be empty");

        assertThatThrownBy(() -> new ForensicsPayload(
                " ",
                ForensicsPayload.Kind.DIAGNOSTIC_REPORT,
                "application/json",
                "payload".getBytes(StandardCharsets.UTF_8),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payloadId must not be blank");

        assertThatThrownBy(() -> new ForensicsPayload(
                "payload-1",
                ForensicsPayload.Kind.DIAGNOSTIC_REPORT,
                "application/json",
                "payload".getBytes(StandardCharsets.UTF_8),
                Map.of(" ", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attribute key must not be blank");

        assertThatThrownBy(() -> new ForensicsSubmission(
                "1",
                "project-a",
                "repo",
                "main",
                "commit",
                "build",
                "timestamp",
                "module",
                ":module",
                "plugin",
                " ",
                List.of(new ForensicsPayload(
                        "payload-1",
                        ForensicsPayload.Kind.DIAGNOSTIC_REPORT,
                        "application/json",
                        "payload".getBytes(StandardCharsets.UTF_8),
                        Map.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pluginVersion must not be blank");
    }
}
