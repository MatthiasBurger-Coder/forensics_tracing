package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForensicsSemanticImportVerifierTest {

    @Test
    void rejectsImportWhenJoernIsDisabled(@TempDir Path tempDir) {
        ForensicsSemanticImportVerifier verifier = new ForensicsSemanticImportVerifier();

        assertThatThrownBy(() -> verifier.verify(false, tempDir, "joernEnabled", "analyzeSemantics"))
                .isInstanceOf(ForensicsSemanticAnalysisException.class)
                .hasMessageContaining("Joern semantic import is disabled");
    }

    @Test
    void rejectsImportWhenSemanticArtifactsAreMissing(@TempDir Path tempDir) {
        ForensicsSemanticImportVerifier verifier = new ForensicsSemanticImportVerifier();

        assertThatThrownBy(() -> verifier.verify(true, tempDir, "joernEnabled", "analyzeSemantics"))
                .isInstanceOf(ForensicsSemanticAnalysisException.class)
                .hasMessageContaining("Joern semantic artifacts are missing");
    }

    @Test
    void acceptsImportWhenCallgraphArtifactExists(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("callgraph.json"), "[]");
        ForensicsSemanticImportVerifier verifier = new ForensicsSemanticImportVerifier();

        assertThatCode(() -> verifier.verify(true, tempDir, "joernEnabled", "analyzeSemantics"))
                .doesNotThrowAnyException();
    }
}
