package de.burger.forensics.adapters.joern;

import de.burger.forensics.adapters.filesystem.ArtifactChecksumService;
import de.burger.forensics.adaptersupport.joern.JoernAnalysisConfig;
import de.burger.forensics.adaptersupport.joern.JoernAnalysisException;
import de.burger.forensics.adaptersupport.joern.JoernCommandExecutor;
import de.burger.forensics.adaptersupport.joern.JoernCommandResult;
import de.burger.forensics.adaptersupport.joern.JoernOutputParser;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisRequest;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JoernCliSemanticAnalysisAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void executesJoernCommandsAndParsesGeneratedArtifacts() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src"));
        RecordingExecutor executor = new RecordingExecutor(tempDir.resolve("joern"));
        JoernCliSemanticAnalysisAdapter adapter = new JoernCliSemanticAnalysisAdapter(
                config(true),
                executor,
                new JoernOutputParser(),
                new ArtifactChecksumService());

        SemanticAnalysisResult result = adapter.analyze(new SemanticAnalysisRequest(
                identity(),
                List.of(sourceRoot.toString()),
                tempDir.resolve("workspace").toString(),
                tempDir.resolve("joern").toString()));

        assertThat(executor.commands).hasSize(5);
        assertThat(result.providerVersion()).isEqualTo("joern 1.2.3");
        assertThat(result.semanticFingerprint()).startsWith("sha256:");
        assertThat(result.artifacts()).extracting("type")
                .contains("joern-cpg", "joern-callgraph", "joern-controlflow", "joern-dataflow", "joern-slices");
        assertThat(result.nodes()).hasSize(1);
        assertThat(result.anchors()).hasSize(1);
    }

    @Test
    void failedJoernCommandThrowsWhenFailOnErrorIsEnabled() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src"));
        RecordingExecutor executor = new RecordingExecutor(tempDir.resolve("joern"));
        executor.failParse = true;
        JoernCliSemanticAnalysisAdapter adapter = new JoernCliSemanticAnalysisAdapter(
                config(true),
                executor,
                new JoernOutputParser(),
                new ArtifactChecksumService());

        assertThatThrownBy(() -> adapter.analyze(new SemanticAnalysisRequest(
                identity(),
                List.of(sourceRoot.toString()),
                tempDir.resolve("workspace").toString(),
                tempDir.resolve("joern").toString())))
                .isInstanceOf(JoernAnalysisException.class)
                .hasMessageContaining("joern-parse");
    }

    @Test
    void failedJoernCommandCanBeToleratedWhenConfigured() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src"));
        RecordingExecutor executor = new RecordingExecutor(tempDir.resolve("joern"));
        executor.failParse = true;
        JoernCliSemanticAnalysisAdapter adapter = new JoernCliSemanticAnalysisAdapter(
                config(false),
                executor,
                new JoernOutputParser(),
                new ArtifactChecksumService());

        SemanticAnalysisResult result = adapter.analyze(new SemanticAnalysisRequest(
                identity(),
                List.of(sourceRoot.toString()),
                tempDir.resolve("workspace").toString(),
                tempDir.resolve("joern").toString()));

        assertThat(result.providerVersion()).isEqualTo("joern 1.2.3");
    }

    @Test
    void failedVersionCommandUsesUnknownVersionAndCreatesMissingSlicesArtifact() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src"));
        RecordingExecutor executor = new RecordingExecutor(tempDir.resolve("joern"));
        executor.failVersion = true;
        executor.skipSlices = true;
        JoernCliSemanticAnalysisAdapter adapter = new JoernCliSemanticAnalysisAdapter(
                config(true),
                executor,
                new JoernOutputParser(),
                new ArtifactChecksumService());

        SemanticAnalysisResult result = adapter.analyze(new SemanticAnalysisRequest(
                identity(),
                List.of(sourceRoot.toString()),
                tempDir.resolve("workspace").toString(),
                tempDir.resolve("joern").toString()));

        assertThat(result.providerVersion()).isEqualTo("UNKNOWN");
        assertThat(Files.readString(tempDir.resolve("joern/slices.json"))).contains("\"anchors\":[]");
    }

    private JoernAnalysisConfig config(boolean failOnError) {
        return new JoernAnalysisConfig(
                Path.of("joern"),
                Path.of("joern-parse"),
                Path.of("joern-slice"),
                Duration.ofSeconds(30),
                failOnError);
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

    private static final class RecordingExecutor implements JoernCommandExecutor {
        private final List<List<String>> commands = new ArrayList<>();
        private final Path outputDirectory;
        private boolean failParse;
        private boolean failVersion;
        private boolean skipSlices;

        private RecordingExecutor(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        @Override
        public JoernCommandResult execute(List<String> command, Duration timeout, Path workingDirectory) {
            commands.add(command);
            try {
                Files.createDirectories(outputDirectory);
                String commandName = Path.of(command.get(0)).getFileName().toString();
                if (commandName.equals("joern") && command.contains("--version")) {
                    if (failVersion) {
                        return new JoernCommandResult(1, "", "version failed");
                    }
                    return new JoernCommandResult(0, "joern 1.2.3", "");
                }
                if (commandName.equals("joern-parse")) {
                    Files.writeString(outputDirectory.resolve("cpg.bin"), "cpg", StandardCharsets.UTF_8);
                    return failParse ? new JoernCommandResult(1, "", "parse failed") : new JoernCommandResult(0, "", "");
                }
                if (commandName.equals("joern") && command.contains("callgraph.sc")) {
                    Files.writeString(outputDirectory.resolve("callgraph.json"), callgraph(), StandardCharsets.UTF_8);
                    return new JoernCommandResult(0, "", "");
                }
                if (commandName.equals("joern") && command.contains("controlflow.sc")) {
                    Files.writeString(outputDirectory.resolve("controlflow.json"), controlflow(), StandardCharsets.UTF_8);
                    return new JoernCommandResult(0, "", "");
                }
                if (commandName.equals("joern-slice")) {
                    Files.writeString(outputDirectory.resolve("dataflow.json"), dataflow(), StandardCharsets.UTF_8);
                    if (!skipSlices) {
                        Files.writeString(outputDirectory.resolve("slices.json"), slices(), StandardCharsets.UTF_8);
                    }
                    return new JoernCommandResult(0, "", "");
                }
                return new JoernCommandResult(1, "", "unexpected command");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String callgraph() {
            return """
                    {"nodes":[{"id":"n1","type":"CALL","file":"Demo.java","fqcn":"demo.Demo","method":"run","signature":"void run()","line":12,"code":"call()"}],
                     "edges":[{"id":"e1","source":"n1","target":"n2","type":"CALL"}],
                     "methods":[{"id":"m1","file":"Demo.java","fqcn":"demo.Demo","name":"run","signature":"void run()","line":12}],
                     "calls":[{"caller":"m1","callee":"m2","node":"n1"}]}
                    """;
        }

        private static String controlflow() {
            return "{\"relations\":[{\"source\":\"n1\",\"target\":\"n2\",\"type\":\"NEXT\"}]}";
        }

        private static String dataflow() {
            return "{\"paths\":[{\"id\":\"p1\",\"source\":\"n1\",\"target\":\"n2\",\"steps\":[{\"node\":\"n1\",\"order\":0,\"kind\":\"SOURCE\"}]}]}";
        }

        private static String slices() {
            return """
                    {"anchors":[{"scanEventKey":"demo.Demo#run:12:METHOD_ENTER","node":"n1","file":"Demo.java","fqcn":"demo.Demo","method":"run","signature":"void run()","line":12,"code":"call()","confidence":0.95,"strategy":"FQCN_METHOD_LINE_CODE"}]}
                    """;
        }
    }
}
