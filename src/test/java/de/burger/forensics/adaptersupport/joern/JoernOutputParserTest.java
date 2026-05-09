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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        assertThatThrownBy(() -> parse(paths, "joern", "sha256:x"))
                .isInstanceOf(JoernAnalysisException.class)
                .hasMessageContaining("method");
    }

    @Test
    void malformedNumericFieldsFailWithDiagnostics() throws Exception {
        JoernArtifactPaths paths = JoernArtifactPaths.under(tempDir);
        Files.writeString(paths.callgraph(), """
                {"nodes":[{"id":"n1","type":"CALL","file":"Demo.java","method":"run","line":1.5,"code":"call()"}]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parse(paths, "joern", "sha256:x"))
                .isInstanceOf(NumberFormatException.class);

        Files.writeString(paths.callgraph(), """
                {"nodes":[{"id":"n1","type":"CALL","file":"Demo.java","method":"run","line":,"code":"call()"}]}
                """, StandardCharsets.UTF_8);
        assertThatThrownBy(() -> parse(paths, "joern", "sha256:x"))
                .isInstanceOf(JoernAnalysisException.class)
                .hasMessageContaining("line");
    }

    @Test
    void malformedTextFieldsFailWithDiagnostics() throws Exception {
        JoernArtifactPaths paths = JoernArtifactPaths.under(tempDir);
        Files.writeString(paths.callgraph(), """
                {"nodes":[{"id":"n1","type":"CALL","file":"Demo.java","line":1,"code":"call()","method":run}]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parse(paths, "joern", "sha256:x"))
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
        Path joern = Path.of("joern");
        Path joernParse = Path.of("joern-parse");
        Path joernSlice = Path.of("joern-slice");
        Duration oneSecond = Duration.ofSeconds(1);
        Duration negativeDuration = Duration.ofSeconds(-1);

        assertThat(config.failOnError()).isTrue();
        assertThat(paths.all()).containsExactly(paths.cpg(), paths.callgraph(), paths.controlflow(), paths.dataflow(), paths.slices());
        assertThatThrownBy(() -> config(null, joernParse, joernSlice, oneSecond))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> config(joern, null, joernSlice, oneSecond))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> config(joern, joernParse, null, oneSecond))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> config(joern, joernParse, joernSlice, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(joern, joernParse, joernSlice, negativeDuration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> artifactPaths(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void processExecutorRejectsEmptyCommands() {
        List<String> command = List.of();
        Duration timeout = Duration.ofSeconds(1);

        assertThatThrownBy(() -> execute(command, timeout, tempDir))
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
        List<String> command = List.of(tempDir.resolve("missing-command").toString());
        Duration timeout = Duration.ofSeconds(1);

        assertThatThrownBy(() -> execute(command, timeout, tempDir))
                .isInstanceOf(IllegalStateException.class)
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

    private static SemanticAnalysisResult parse(JoernArtifactPaths paths, String providerVersion, String semanticFingerprint) {
        return new JoernOutputParser().parse(paths, List.of(), providerVersion, semanticFingerprint);
    }

    private static JoernAnalysisConfig config(Path joernExecutable,
                                              Path joernParseExecutable,
                                              Path joernSliceExecutable,
                                              Duration timeout) {
        return new JoernAnalysisConfig(joernExecutable, joernParseExecutable, joernSliceExecutable, timeout, true);
    }

    private static JoernArtifactPaths artifactPaths(Path root) {
        return JoernArtifactPaths.under(root);
    }

    private static JoernCommandResult execute(List<String> command, Duration timeout, Path workingDirectory) {
        return new ProcessJoernCommandExecutor().execute(command, timeout, workingDirectory);
    }

    public static final class SleepProcess {
        public static void main(String[] args) throws Exception {
            new CountDownLatch(1).await(10L, TimeUnit.SECONDS);
        }
    }
}
