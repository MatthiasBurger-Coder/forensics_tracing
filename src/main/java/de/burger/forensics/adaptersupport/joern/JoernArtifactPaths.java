package de.burger.forensics.adaptersupport.joern;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Standard artifact paths produced by a Joern analysis run.
 */
public record JoernArtifactPaths(Path outputDirectory,
                                 Path cpg,
                                 Path callgraph,
                                 Path controlflow,
                                 Path dataflow,
                                 Path slices) {

    public static JoernArtifactPaths under(Path outputDirectory) {
        Objects.requireNonNull(outputDirectory, "Output directory must not be null.");
        return new JoernArtifactPaths(
                outputDirectory,
                outputDirectory.resolve("cpg.bin"),
                outputDirectory.resolve("callgraph.json"),
                outputDirectory.resolve("controlflow.json"),
                outputDirectory.resolve("dataflow.json"),
                outputDirectory.resolve("slices.json"));
    }

    public JoernArtifactPaths {
        Objects.requireNonNull(outputDirectory, "Output directory must not be null.");
        Objects.requireNonNull(cpg, "CPG path must not be null.");
        Objects.requireNonNull(callgraph, "Callgraph path must not be null.");
        Objects.requireNonNull(controlflow, "Controlflow path must not be null.");
        Objects.requireNonNull(dataflow, "Dataflow path must not be null.");
        Objects.requireNonNull(slices, "Slices path must not be null.");
    }

    public List<Path> all() {
        return List.of(cpg, callgraph, controlflow, dataflow, slices);
    }
}
