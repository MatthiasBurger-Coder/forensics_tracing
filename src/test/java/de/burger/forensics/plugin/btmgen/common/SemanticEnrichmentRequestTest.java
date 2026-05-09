package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticEnrichmentRequestTest {

    @Test
    void copiesSourceRoots(@TempDir Path tempDir) {
        List<Path> sourceRoots = new ArrayList<>();
        sourceRoots.add(tempDir.resolve("src/main/java"));

        SemanticEnrichmentRequest request = request(sourceRoots, tempDir, 30);
        sourceRoots.add(tempDir.resolve("src/test/java"));

        assertThat(request.sourceRoots()).containsExactly(tempDir.resolve("src/main/java"));
    }

    @Test
    void requiresPositiveTimeout(@TempDir Path tempDir) {
        assertThatThrownBy(() -> request(List.of(tempDir.resolve("src/main/java")), tempDir, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("joernTimeoutSeconds must be greater than zero");
    }

    private static SemanticEnrichmentRequest request(List<Path> sourceRoots, Path tempDir, int timeoutSeconds) {
        return new SemanticEnrichmentRequest(
                sourceRoots,
                tempDir.resolve("bin/joern"),
                tempDir.resolve("bin/joern-parse"),
                tempDir.resolve("bin/joern-slice"),
                tempDir.resolve("target/forensics/joern/workspace"),
                tempDir.resolve("target/forensics/joern"),
                timeoutSeconds,
                true,
                tempDir.resolve("target/forensics/analysis-store"),
                tempDir.resolve("target/forensics/manifest.json"),
                tempDir.resolve("target/forensics/checksums.sha256"),
                tempDir.resolve("target/forensics/generated.btm"));
    }
}
