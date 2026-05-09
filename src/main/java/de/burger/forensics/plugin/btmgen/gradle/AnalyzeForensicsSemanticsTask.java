package de.burger.forensics.plugin.btmgen.gradle;

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
import de.burger.forensics.plugin.btmgen.common.BtmGenerationDefaults;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Runs optional semantic enrichment for a generated forensics analysis package.
 */
@DisableCachingByDefault(because = "The task invokes external Joern processes and updates local H2 analysis state.")
public abstract class AnalyzeForensicsSemanticsTask extends DefaultTask {

    @Inject
    @SuppressWarnings("java:S5993")
    public AnalyzeForensicsSemanticsTask() {
        applyDefaultConventions();
    }

    @Input
    public abstract Property<@NotNull Boolean> getJoernEnabled();

    @Input
    public abstract Property<@NotNull Boolean> getJoernFailOnError();

    @Input
    public abstract Property<@NotNull Integer> getJoernTimeoutSeconds();

    @Input
    @Optional
    public abstract Property<@NotNull String> getJoernMaxHeap();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getJoernExecutables();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceRoots();

    @OutputDirectory
    public abstract DirectoryProperty getJoernOutputDirectory();

    @LocalState
    public abstract DirectoryProperty getJoernWorkspaceDirectory();

    @LocalState
    public abstract DirectoryProperty getAnalysisStoreDirectory();

    @OutputFile
    public abstract RegularFileProperty getManifestFile();

    @OutputFile
    public abstract RegularFileProperty getChecksumsFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void analyze() {
        applyDefaultConventions();
        if (!getJoernEnabled().getOrElse(false)) {
            throw new GradleException("Joern semantic analysis is disabled. Set btmGen.joernEnabled=true to run it.");
        }
        BuildIdentity identity = readIdentity(getManifestFile().get().getAsFile().toPath());
        Path databaseFile = getAnalysisStoreDirectory().get().getAsFile().toPath()
                .resolve(BtmGenerationDefaults.DEFAULT_ANALYSIS_STORE_DATABASE_FILE_NAME);
        ArtifactChecksumService checksumService = new ArtifactChecksumService();
        H2AnalysisStoreAdapter store = analysisStore(databaseFile);
        SemanticAnalysisResult result;
        try {
            store.initializeSchema();
            SemanticAnalysisPort adapter = semanticAnalysisPort(
                    new JoernAnalysisConfig(
                            joernExecutable("joern"),
                            joernExecutable("joern-parse"),
                            joernExecutable("joern-slice"),
                            Duration.ofSeconds(getJoernTimeoutSeconds().getOrElse(300)),
                            getJoernFailOnError().getOrElse(true)),
                    checksumService);
            result = new AnalyzeSemanticsUseCase(adapter, store).analyze(new SemanticAnalysisRequest(
                    identity,
                    sourceRoots(),
                    getJoernWorkspaceDirectory().get().getAsFile().toString(),
                    getJoernOutputDirectory().get().getAsFile().toString()));
            store.storeArtifactChecksums(identity.analysisRunId(), result.artifacts());
        } finally {
            store.close();
        }
        writeUpdatedArtifacts(identity, result, checksumService);
    }

    protected SemanticAnalysisPort semanticAnalysisPort(
            JoernAnalysisConfig config,
            ArtifactChecksumService checksumService
    ) {
        return new JoernCliSemanticAnalysisAdapter(config, checksumService);
    }

    protected H2AnalysisStoreAdapter analysisStore(Path databaseFile) {
        return new H2AnalysisStoreAdapter(databaseFile);
    }

    public void setExtension(BtmGenExtension extension) {
        Objects.requireNonNull(extension, "Extension must not be null.");
        applyDefaultConventions();
        getJoernEnabled().convention(extension.getJoernEnabled());
        getJoernFailOnError().convention(extension.getJoernFailOnError());
        getJoernTimeoutSeconds().convention(extension.getJoernTimeoutSeconds());
        getJoernMaxHeap().convention(extension.getJoernMaxHeap());
        getJoernOutputDirectory().convention(getProjectLayout().dir(extension.getJoernOutputDirectory()));
        getJoernWorkspaceDirectory().convention(getProjectLayout().dir(extension.getJoernWorkspaceDirectory()));
        getAnalysisStoreDirectory().convention(getProjectLayout().dir(extension.getAnalysisStoreDirectory()));
        getManifestFile().set(getProjectLayout().file(extension.getManifestFile()));
        getChecksumsFile().set(getProjectLayout().file(extension.getChecksumsFile()));
        getOutputFile().set(getProjectLayout().file(extension.getOutputFile()));
        getJoernExecutables().setFrom(
                extension.getJoernExecutable(),
                extension.getJoernParseExecutable(),
                extension.getJoernSliceExecutable());
        getSourceRoots().setFrom(extension.getSourceRoot(), extension.getSourceRoots());
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    private void applyDefaultConventions() {
        ProjectLayout layout = getProjectLayout();
        conventionIfMissing(getJoernEnabled(), false);
        conventionIfMissing(getJoernFailOnError(), true);
        conventionIfMissing(getJoernTimeoutSeconds(), 300);
        conventionIfMissing(getJoernMaxHeap(), "");
        conventionIfMissing(getJoernOutputDirectory(), layout.getBuildDirectory().dir("forensics/joern"));
        conventionIfMissing(getJoernWorkspaceDirectory(), layout.getBuildDirectory().dir("forensics/joern/workspace"));
        conventionIfMissing(getAnalysisStoreDirectory(), layout.getBuildDirectory().dir("forensics/analysis-store"));
        conventionIfMissing(getManifestFile(), layout.getBuildDirectory().file("forensics/manifest.json"));
        conventionIfMissing(getChecksumsFile(), layout.getBuildDirectory().file("forensics/checksums.sha256"));
        conventionIfMissing(getOutputFile(), layout.getBuildDirectory().file("forensics/forensics.btm"));
    }

    private void writeUpdatedArtifacts(
            BuildIdentity identity,
            SemanticAnalysisResult result,
            ArtifactChecksumService checksumService
    ) {
        Path manifest = getManifestFile().get().getAsFile().toPath();
        Path baseDirectory = manifest.toAbsolutePath().normalize().getParent();
        if (baseDirectory == null) {
            baseDirectory = Path.of(".").toAbsolutePath().normalize();
        }
        Path btmFile = getOutputFile().get().getAsFile().toPath();
        if (!Files.exists(btmFile)) {
            throw new GradleException("Generated BTM file is missing: " + btmFile);
        }
        ArtifactChecksum btmChecksum = checksumService.checksumFile(baseDirectory, btmFile, "byteman-rules");
        ArtifactChecksum storeChecksum = checksumService.checksumDirectory(
                baseDirectory,
                getAnalysisStoreDirectory().get().getAsFile().toPath(),
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
                getAnalysisStoreDirectory().get().getAsFile().toPath(),
                "h2-analysis-store-file"));
        checksumEntries.addAll(result.artifacts());
        new ChecksumFileWriter().write(getChecksumsFile().get().getAsFile().toPath(), checksumEntries);
    }

    private Path joernExecutable(String executableName) {
        return getJoernExecutables().getFiles().stream()
                .filter(file -> file.getName().equals(executableName) || file.getName().equals(executableName + ".bat"))
                .findFirst()
                .orElseGet(() -> new File(executableName))
                .toPath();
    }

    private List<String> sourceRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        getSourceRoots().getFiles().stream()
                .sorted(Comparator.comparing(file -> file.toPath().toAbsolutePath().normalize().toString()))
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .filter(Files::exists)
                .forEach(roots::add);
        if (roots.isEmpty()) {
            throw new GradleException("No source roots are available for Joern semantic analysis.");
        }
        return roots.stream().map(Path::toString).toList();
    }

    private static BuildIdentity readIdentity(Path manifestFile) {
        if (!Files.exists(manifestFile)) {
            throw new GradleException("Analysis manifest is missing: " + manifestFile);
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
            throw new GradleException("Analysis manifest is missing field: " + fieldName);
        }
        int colon = json.indexOf(':', fieldStart + marker.length());
        int firstQuote = json.indexOf('"', colon + 1);
        if (colon < 0 || firstQuote < 0) {
            throw new GradleException("Analysis manifest has invalid field: " + fieldName);
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
        throw new GradleException("Analysis manifest has unterminated field: " + fieldName);
    }

    private static <T> void conventionIfMissing(Property<T> property, T value) {
        if (!property.isPresent()) {
            property.convention(value);
        }
    }

    private static void conventionIfMissing(DirectoryProperty property, Provider<Directory> value) {
        if (!property.isPresent()) {
            property.convention(value);
        }
    }

    private static void conventionIfMissing(RegularFileProperty property, Provider<RegularFile> value) {
        if (!property.isPresent()) {
            property.convention(value);
        }
    }
}
