package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.adapters.filesystem.AnalysisManifestWriter;
import de.burger.forensics.adapters.filesystem.ArtifactChecksumService;
import de.burger.forensics.adapters.filesystem.ChecksumFileWriter;
import de.burger.forensics.adapters.joern.JoernCliSemanticAnalysisAdapter;
import de.burger.forensics.adapters.persistence.h2.H2AnalysisStoreAdapter;
import de.burger.forensics.adaptersupport.joern.JoernAnalysisConfig;
import de.burger.forensics.application.service.AnalyzeSemanticsUseCase;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisRequest;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.port.out.SemanticAnalysisPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared orchestration for optional Joern semantic enrichment and Analysis Store import.
 */
public final class SemanticEnrichmentRunner {

    private final SemanticAnalysisPortFactory semanticAnalysisPortFactory;
    private final AnalysisStoreFactory analysisStoreFactory;

    public SemanticEnrichmentRunner() {
        this(JoernCliSemanticAnalysisAdapter::new, H2AnalysisStoreAdapter::new);
    }

    public SemanticEnrichmentRunner(
            SemanticAnalysisPortFactory semanticAnalysisPortFactory,
            AnalysisStoreFactory analysisStoreFactory
    ) {
        this.semanticAnalysisPortFactory = Objects.requireNonNull(
                semanticAnalysisPortFactory,
                "semanticAnalysisPortFactory");
        this.analysisStoreFactory = Objects.requireNonNull(analysisStoreFactory, "analysisStoreFactory");
    }

    public SemanticAnalysisResult analyze(SemanticEnrichmentRequest request) {
        Objects.requireNonNull(request, "request");
        BuildIdentity identity = readIdentity(request.manifestFile());
        Path databaseFile = request.analysisStoreDirectory()
                .resolve(BtmGenerationDefaults.DEFAULT_ANALYSIS_STORE_DATABASE_FILE_NAME);
        ArtifactChecksumService checksumService = new ArtifactChecksumService();
        H2AnalysisStoreAdapter store = analysisStoreFactory.create(databaseFile);
        SemanticAnalysisResult result;
        try {
            store.initializeSchema();
            SemanticAnalysisPort adapter = semanticAnalysisPortFactory.create(
                    new JoernAnalysisConfig(
                            request.joernExecutable(),
                            request.joernParseExecutable(),
                            request.joernSliceExecutable(),
                            Duration.ofSeconds(request.joernTimeoutSeconds()),
                            request.joernFailOnError()),
                    checksumService);
            result = new AnalyzeSemanticsUseCase(adapter, store).analyze(new SemanticAnalysisRequest(
                    identity,
                    sourceRoots(request.sourceRoots()),
                    request.joernWorkspaceDirectory().toString(),
                    request.joernOutputDirectory().toString()));
            store.storeArtifactChecksums(identity.analysisRunId(), result.artifacts());
        } finally {
            store.close();
        }
        writeUpdatedArtifacts(request, identity, result, checksumService);
        return result;
    }

    private static void writeUpdatedArtifacts(
            SemanticEnrichmentRequest request,
            BuildIdentity identity,
            SemanticAnalysisResult result,
            ArtifactChecksumService checksumService
    ) {
        Path manifest = request.manifestFile();
        Path baseDirectory = manifest.toAbsolutePath().normalize().getParent();
        if (baseDirectory == null) {
            baseDirectory = Path.of(".").toAbsolutePath().normalize();
        }
        Path btmFile = request.outputFile();
        if (!Files.exists(btmFile)) {
            throw new BtmGenerationException("Generated BTM file is missing: " + btmFile);
        }
        ArtifactChecksum btmChecksum = checksumService.checksumFile(baseDirectory, btmFile, "byteman-rules");
        ArtifactChecksum storeChecksum = checksumService.checksumDirectory(
                baseDirectory,
                request.analysisStoreDirectory(),
                "h2-analysis-store");
        List<ArtifactChecksum> manifestArtifacts = new ArrayList<>();
        manifestArtifacts.add(btmChecksum);
        manifestArtifacts.add(storeChecksum);
        manifestArtifacts.addAll(result.artifacts());
        new AnalysisManifestWriter().write(manifest, identity, manifestArtifacts, result);
        ArtifactChecksum manifestChecksum = checksumService.checksumFile(baseDirectory, manifest, "analysis-manifest");
        List<ArtifactChecksum> checksumEntries = new ArrayList<>();
        checksumEntries.add(btmChecksum);
        checksumEntries.add(manifestChecksum);
        checksumEntries.addAll(checksumService.checksumFiles(
                baseDirectory,
                request.analysisStoreDirectory(),
                "h2-analysis-store-file"));
        checksumEntries.addAll(result.artifacts());
        new ChecksumFileWriter().write(request.checksumsFile(), checksumEntries);
    }

    private static List<String> sourceRoots(List<Path> roots) {
        List<String> existingRoots = roots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::exists)
                .map(Path::toString)
                .toList();
        if (existingRoots.isEmpty()) {
            throw new BtmGenerationException("No source roots are available for Joern semantic analysis.");
        }
        return existingRoots;
    }

    private static BuildIdentity readIdentity(Path manifestFile) {
        if (!Files.exists(manifestFile)) {
            throw new BtmGenerationException("Analysis manifest is missing: " + manifestFile);
        }
        try {
            String manifest = Files.readString(manifestFile, StandardCharsets.UTF_8);
            return new BuildIdentity(
                    field(manifest, "projectKey"),
                    new AnalysisRunId(field(manifest, "analysisRunId")),
                    new BuildId(field(manifest, "buildId")),
                    new SourceFingerprint(field(manifest, "sourceFingerprint")),
                    BuildIdentity.NOT_COMPUTED,
                    field(manifest, "btmRulesFingerprint"),
                    BuildIdentity.NOT_COMPUTED,
                    field(manifest, "pluginVersion"),
                    new AnalysisSchemaVersion(field(manifest, "schemaVersion")),
                    Instant.parse(field(manifest, "createdAt")));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read analysis manifest " + manifestFile + ".", e);
        }
    }

    private static String field(String json, String fieldName) {
        String marker = "\"" + fieldName + "\"";
        int fieldStart = json.indexOf(marker);
        if (fieldStart < 0) {
            throw new BtmGenerationException("Analysis manifest is missing field: " + fieldName);
        }
        int colon = json.indexOf(':', fieldStart + marker.length());
        int firstQuote = json.indexOf('"', colon + 1);
        if (colon < 0 || firstQuote < 0) {
            throw new BtmGenerationException("Analysis manifest has invalid field: " + fieldName);
        }
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int index = firstQuote + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaped) {
                builder.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return builder.toString();
            } else {
                builder.append(current);
            }
        }
        throw new BtmGenerationException("Analysis manifest has unterminated field: " + fieldName);
    }

    @FunctionalInterface
    public interface SemanticAnalysisPortFactory {
        SemanticAnalysisPort create(JoernAnalysisConfig config, ArtifactChecksumService checksumService);
    }

    @FunctionalInterface
    public interface AnalysisStoreFactory {
        H2AnalysisStoreAdapter create(Path databaseFile);
    }
}
