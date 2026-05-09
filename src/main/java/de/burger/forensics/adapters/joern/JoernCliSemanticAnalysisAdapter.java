package de.burger.forensics.adapters.joern;

import de.burger.forensics.adaptersupport.joern.JoernAnalysisConfig;
import de.burger.forensics.adaptersupport.joern.JoernAnalysisException;
import de.burger.forensics.adaptersupport.joern.JoernArtifactPaths;
import de.burger.forensics.adaptersupport.joern.JoernCommandExecutor;
import de.burger.forensics.adaptersupport.joern.JoernCommandResult;
import de.burger.forensics.adaptersupport.joern.JoernOutputParser;
import de.burger.forensics.adaptersupport.joern.ProcessJoernCommandExecutor;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisRequest;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.port.out.ArtifactChecksumPort;
import de.burger.forensics.domain.port.out.SemanticAnalysisPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Semantic analysis adapter that invokes Joern as an external CLI.
 */
public final class JoernCliSemanticAnalysisAdapter implements SemanticAnalysisPort {

    private final JoernAnalysisConfig config;
    private final JoernCommandExecutor executor;
    private final JoernOutputParser parser;
    private final ArtifactChecksumPort checksumPort;

    public JoernCliSemanticAnalysisAdapter(JoernAnalysisConfig config, ArtifactChecksumPort checksumPort) {
        this(config, new ProcessJoernCommandExecutor(), new JoernOutputParser(), checksumPort);
    }

    public JoernCliSemanticAnalysisAdapter(
            JoernAnalysisConfig config,
            JoernCommandExecutor executor,
            JoernOutputParser parser,
            ArtifactChecksumPort checksumPort
    ) {
        this.config = Objects.requireNonNull(config, "Joern analysis config must not be null.");
        this.executor = Objects.requireNonNull(executor, "Joern command executor must not be null.");
        this.parser = Objects.requireNonNull(parser, "Joern output parser must not be null.");
        this.checksumPort = Objects.requireNonNull(checksumPort, "Artifact checksum port must not be null.");
    }

    @Override
    public SemanticAnalysisResult analyze(SemanticAnalysisRequest request) {
        Objects.requireNonNull(request, "Semantic analysis request must not be null.");
        Path outputDirectory = Path.of(request.outputDirectory()).toAbsolutePath().normalize();
        Path workspaceDirectory = Path.of(request.workspaceDirectory()).toAbsolutePath().normalize();
        JoernArtifactPaths artifacts = JoernArtifactPaths.under(outputDirectory);
        createDirectories(outputDirectory, workspaceDirectory);

        String providerVersion = runVersion(workspaceDirectory);
        runParse(request, artifacts, workspaceDirectory);
        runExport("callgraph", artifacts.cpg(), artifacts.callgraph(), workspaceDirectory);
        runExport("controlflow", artifacts.cpg(), artifacts.controlflow(), workspaceDirectory);
        runSlice(artifacts, workspaceDirectory);

        List<ArtifactChecksum> checksums = artifactChecksums(outputDirectory, artifacts);
        String semanticFingerprint = "sha256:" + sha256(checksums.stream()
                .map(checksum -> checksum.type() + "=" + checksum.sha256())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(""));
        return parser.parse(artifacts, checksums, providerVersion, semanticFingerprint);
    }

    private String runVersion(Path workspaceDirectory) {
        JoernCommandResult result = execute(List.of(config.joernExecutable().toString(), "--version"), workspaceDirectory);
        if (!result.successful()) {
            return "UNKNOWN";
        }
        String version = result.stdout().isBlank() ? result.stderr() : result.stdout();
        return version.isBlank() ? "UNKNOWN" : version.strip();
    }

    private void runParse(SemanticAnalysisRequest request, JoernArtifactPaths artifacts, Path workspaceDirectory) {
        List<String> command = new ArrayList<>();
        command.add(config.joernParseExecutable().toString());
        command.add("--output");
        command.add(artifacts.cpg().toString());
        command.addAll(request.sourceRoots());
        requireSuccess("joern-parse", execute(command, workspaceDirectory));
    }

    private void runExport(String kind, Path cpg, Path output, Path workspaceDirectory) {
        requireSuccess(kind, execute(List.of(
                config.joernExecutable().toString(),
                "--script",
                kind + ".sc",
                "--params",
                "cpg=" + cpg + ",out=" + output), workspaceDirectory));
    }

    private void runSlice(JoernArtifactPaths artifacts, Path workspaceDirectory) {
        requireSuccess("joern-slice", execute(List.of(
                config.joernSliceExecutable().toString(),
                "data-flow",
                "--out",
                artifacts.dataflow().toString(),
                artifacts.cpg().toString()), workspaceDirectory));
        if (!Files.exists(artifacts.slices())) {
            writeEmptySlices(artifacts.slices());
        }
    }

    private JoernCommandResult execute(List<String> command, Path workspaceDirectory) {
        return executor.execute(command, config.timeout(), workspaceDirectory);
    }

    private void requireSuccess(String operation, JoernCommandResult result) {
        if (result.successful()) {
            return;
        }
        String message = "Joern " + operation + " failed with exit code " + result.exitCode()
                + ". stderr: " + result.stderr();
        if (config.failOnError()) {
            throw new JoernAnalysisException(message);
        }
    }

    private List<ArtifactChecksum> artifactChecksums(Path outputDirectory, JoernArtifactPaths artifacts) {
        return artifacts.all().stream()
                .filter(Files::exists)
                .map(path -> checksumPort.checksumFile(outputDirectory, path, artifactType(path)))
                .toList();
    }

    private static String artifactType(Path path) {
        String fileName = path.getFileName().toString();
        return switch (fileName) {
            case "cpg.bin" -> "joern-cpg";
            case "callgraph.json" -> "joern-callgraph";
            case "controlflow.json" -> "joern-controlflow";
            case "dataflow.json" -> "joern-dataflow";
            case "slices.json" -> "joern-slices";
            default -> "joern-artifact";
        };
    }

    private static void createDirectories(Path outputDirectory, Path workspaceDirectory) {
        try {
            Files.createDirectories(outputDirectory);
            Files.createDirectories(workspaceDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create Joern working directories.", e);
        }
    }

    private static void writeEmptySlices(Path slices) {
        try {
            Files.writeString(slices, "{\"anchors\":[]}", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create empty Joern slices artifact " + slices + ".", e);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
