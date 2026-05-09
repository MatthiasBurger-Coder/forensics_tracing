package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRunner;
import de.burger.forensics.plugin.btmgen.common.SemanticEnrichmentRequest;
import de.burger.forensics.plugin.btmgen.common.SemanticEnrichmentRunner;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared Maven Mojo operations that remain independent from Maven lifecycle classes.
 */
final class MavenForensicsMojoSupport {

    private MavenForensicsMojoSupport() {
    }

    static BtmGenerationResult generateBtm(MavenBtmGenParameters parameters, Log log) {
        return new BtmGenerationRunner(
                StrategyRegistries.defaultRegistry(),
                new MavenLogAdapter(log)
        ).generate(parameters.toGenerationRequest());
    }

    static void analyzeSemantics(SemanticEnrichmentRequest request) {
        new SemanticEnrichmentRunner().analyze(request);
    }

    static void requireJoernEnabled(boolean joernEnabled, String goal) throws MojoExecutionException {
        if (!joernEnabled) {
            throw new MojoExecutionException(
                    "Joern semantic analysis is disabled. Set -Dforensics.joernEnabled=true to run " + goal + ".");
        }
    }

    static void requireAnalysisStoreEnabled(boolean analysisStoreEnabled, String goal) throws MojoExecutionException {
        if (!analysisStoreEnabled) {
            throw new MojoExecutionException(
                    "Analysis Store is required for " + goal + ". Set -Dforensics.analysisStoreEnabled=true.");
        }
    }

    static void verifyImportedArtifacts(boolean joernEnabled, File joernOutputDirectory) throws MojoExecutionException {
        requireJoernEnabled(joernEnabled, "forensics:import-semantics");
        Path callgraph = filePath(joernOutputDirectory, "target/forensics/joern").resolve("callgraph.json");
        if (!Files.isRegularFile(callgraph)) {
            throw new MojoExecutionException("Joern semantic artifacts are missing. Run forensics:analyze-semantics first.");
        }
    }

    static SemanticEnrichmentRequest semanticRequest(
            BtmGenerationRequest generationRequest,
            File joernExecutable,
            File joernParseExecutable,
            File joernSliceExecutable,
            File joernWorkspaceDirectory,
            File joernOutputDirectory,
            int joernTimeoutSeconds,
            boolean joernFailOnError
    ) {
        return new SemanticEnrichmentRequest(
                generationRequest.sourceRoots(),
                filePath(joernExecutable, "joern"),
                filePath(joernParseExecutable, "joern-parse"),
                filePath(joernSliceExecutable, "joern-slice"),
                filePath(joernWorkspaceDirectory, "target/forensics/joern/workspace"),
                filePath(joernOutputDirectory, "target/forensics/joern"),
                joernTimeoutSeconds,
                joernFailOnError,
                generationRequest.analysisStoreDirectory(),
                generationRequest.manifestFile(),
                generationRequest.checksumsFile(),
                generationRequest.outputFile());
    }

    private static Path filePath(File configuredFile, String defaultPath) {
        return configuredFile == null
                ? Path.of(defaultPath).toAbsolutePath().normalize()
                : configuredFile.toPath().toAbsolutePath().normalize();
    }
}
