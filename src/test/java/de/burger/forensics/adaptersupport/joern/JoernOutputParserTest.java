package de.burger.forensics.adaptersupport.joern;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JoernOutputParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesDeterministicJoernJsonArtifacts() throws Exception {
        JoernArtifactPaths paths = JoernArtifactPaths.under(tempDir);
        Files.writeString(paths.callgraph(), """
                {
                  "nodes": [
                    {"id":"n1","type":"CALL","file":"src/Demo.java","fqcn":"demo.Demo","method":"run","signature":"void run()","line":12,"code":"call\\n()"}
                  ],
                  "edges": [
                    {"id":"e1","source":"n1","target":"n2","type":"CALL"}
                  ],
                  "methods": [
                    {"id":"m1","file":"src/Demo.java","fqcn":"demo.Demo","name":"run","signature":"void run()","line":10}
                  ],
                  "calls": [
                    {"caller":"m1","callee":"m2","node":"n1"}
                  ]
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(paths.controlflow(), """
                {"relations":[{"source":"n1","target":"n2","type":"NEXT"}]}
                """, StandardCharsets.UTF_8);
        Files.writeString(paths.dataflow(), """
                {"paths":[{"id":"p1","source":"n1","target":"n2","steps":[{"node":"n1","order":0,"kind":"SOURCE"},{"node":"n2","order":1,"kind":"SINK"}]}]}
                """, StandardCharsets.UTF_8);
        Files.writeString(paths.slices(), """
                {"anchors":[{"scanEventKey":"demo.Demo#run:12:METHOD_ENTER","node":"n1","file":"src/Demo.java","fqcn":"demo.Demo","method":"run","signature":"void run()","line":12,"code":"call()","confidence":0.95,"strategy":"FQCN_METHOD_LINE_CODE"}]}
                """, StandardCharsets.UTF_8);

        SemanticAnalysisResult result = new JoernOutputParser().parse(
                paths,
                List.of(new ArtifactChecksum("cpg.bin", "joern-cpg", "abc", 3L)),
                "joern 1.0",
                "sha256:semantic");

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.edges()).hasSize(1);
        assertThat(result.methods()).hasSize(1);
        assertThat(result.callRelations()).hasSize(1);
        assertThat(result.controlFlowRelations()).hasSize(1);
        assertThat(result.dataFlowPaths()).singleElement()
                .satisfies(path -> assertThat(path.steps()).hasSize(2));
        assertThat(result.anchors()).singleElement()
                .satisfies(anchor -> assertThat(anchor.matchStrategy()).isEqualTo("FQCN_METHOD_LINE_CODE"));
    }

    @Test
    void missingOptionalArtifactsProduceEmptyCollections() {
        SemanticAnalysisResult result = new JoernOutputParser().parse(
                JoernArtifactPaths.under(tempDir),
                List.of(),
                "UNKNOWN",
                "sha256:empty");

        assertThat(result.nodes()).isEmpty();
        assertThat(result.anchors()).isEmpty();
    }

    @Test
    void malformedArraysAreIgnoredAsEmptyArtifacts() throws Exception {
        JoernArtifactPaths paths = JoernArtifactPaths.under(tempDir);
        Files.writeString(paths.callgraph(), """
                {"nodes":[{"id":"n1"}
                """, StandardCharsets.UTF_8);

        SemanticAnalysisResult result = new JoernOutputParser().parse(paths, List.of(), "UNKNOWN", "sha256:empty");

        assertThat(result.nodes()).isEmpty();
    }

    @Test
    void missingRequiredJsonFieldFailsWithDiagnostic() throws Exception {
        JoernArtifactPaths paths = JoernArtifactPaths.under(tempDir);
        Files.writeString(paths.callgraph(), """
                {"nodes":[{"id":"n1","type":"CALL","file":"Demo.java","line":1}]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new JoernOutputParser().parse(paths, List.of(), "joern", "sha256:x"))
                .isInstanceOf(JoernAnalysisException.class)
                .hasMessageContaining("method");
    }

    @Test
    void malformedNumericFieldsFailWithDiagnostics() throws Exception {
        JoernArtifactPaths paths = JoernArtifactPaths.under(tempDir);
        Files.writeString(paths.callgraph(), """
                {"nodes":[{"id":"n1","type":"CALL","file":"Demo.java","method":"run","line":1.5,"code":"call()"}]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new JoernOutputParser().parse(paths, List.of(), "joern", "sha256:x"))
                .isInstanceOf(NumberFormatException.class);

        Files.writeString(paths.callgraph(), """
                {"nodes":[{"id":"n1","type":"CALL","file":"Demo.java","method":"run","line":,"code":"call()"}]}
                """, StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new JoernOutputParser().parse(paths, List.of(), "joern", "sha256:x"))
                .isInstanceOf(JoernAnalysisException.class)
                .hasMessageContaining("line");
    }

    @Test
    void malformedTextFieldsFailWithDiagnostics() throws Exception {
        JoernArtifactPaths paths = JoernArtifactPaths.under(tempDir);
        Files.writeString(paths.callgraph(), """
                {"nodes":[{"id":"n1","type":"CALL","file":"Demo.java","line":1,"code":"call()","method":run}]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new JoernOutputParser().parse(paths, List.of(), "joern", "sha256:x"))
                .isInstanceOf(JoernAnalysisException.class)
                .hasMessageContaining("method");
    }

    @Test
    void commandResultNormalizesNullStreamsAndReportsSuccess() {
        JoernCommandResult success = new JoernCommandResult(0, null, null);
        JoernCommandResult failure = new JoernCommandResult(2, "out", "err");

        assertThat(success.successful()).isTrue();
        assertThat(success.stdout()).isEmpty();
        assertThat(success.stderr()).isEmpty();
        assertThat(failure.successful()).isFalse();
    }

    @Test
    void configAndArtifactPathsValidateRequiredValues() {
        JoernAnalysisConfig config = new JoernAnalysisConfig(
                Path.of("joern"),
                Path.of("joern-parse"),
                Path.of("joern-slice"),
                Duration.ofSeconds(1),
                true);
        JoernArtifactPaths paths = JoernArtifactPaths.under(tempDir);

        assertThat(config.failOnError()).isTrue();
        assertThat(paths.all()).containsExactly(paths.cpg(), paths.callgraph(), paths.controlflow(), paths.dataflow(), paths.slices());
        assertThatThrownBy(() -> new JoernAnalysisConfig(
                null,
                Path.of("joern-parse"),
                Path.of("joern-slice"),
                Duration.ofSeconds(1),
                true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JoernAnalysisConfig(
                Path.of("joern"),
                null,
                Path.of("joern-slice"),
                Duration.ofSeconds(1),
                true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JoernAnalysisConfig(
                Path.of("joern"),
                Path.of("joern-parse"),
                null,
                Duration.ofSeconds(1),
                true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JoernAnalysisConfig(
                Path.of("joern"),
                Path.of("joern-parse"),
                Path.of("joern-slice"),
                Duration.ZERO,
                true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JoernAnalysisConfig(
                Path.of("joern"),
                Path.of("joern-parse"),
                Path.of("joern-slice"),
                Duration.ofSeconds(-1),
                true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JoernArtifactPaths.under(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void processExecutorRejectsEmptyCommands() {
        assertThatThrownBy(() -> new ProcessJoernCommandExecutor().execute(List.of(), Duration.ofSeconds(1), tempDir))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processExecutorCapturesSuccessfulProcessOutput() {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", windows() ? "java.exe" : "java");

        JoernCommandResult result = new ProcessJoernCommandExecutor().execute(
                List.of(javaExecutable.toString(), "-version"),
                Duration.ofSeconds(10),
                tempDir);

        assertThat(result.successful()).isTrue();
        assertThat(result.stderr() + result.stdout()).contains("version");
    }

    @Test
    void processExecutorWrapsMissingCommandFailures() {
        assertThatThrownBy(() -> new ProcessJoernCommandExecutor().execute(
                List.of(tempDir.resolve("missing-command").toString()),
                Duration.ofSeconds(1),
                tempDir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to execute Joern command");
    }

    @Test
    void processExecutorReportsTimeouts() {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", windows() ? "java.exe" : "java");

        JoernCommandResult result = new ProcessJoernCommandExecutor().execute(
                List.of(
                        javaExecutable.toString(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        SleepProcess.class.getName()),
                Duration.ofMillis(1),
                tempDir);

        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.stderr()).contains("timed out");
    }

    private static boolean windows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    public static final class SleepProcess {
        public static void main(String[] args) throws Exception {
            Thread.sleep(10_000L);
        }
    }
}
