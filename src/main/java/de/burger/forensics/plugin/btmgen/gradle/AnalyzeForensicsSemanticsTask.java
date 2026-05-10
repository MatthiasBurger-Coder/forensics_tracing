package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.adapters.filesystem.ArtifactChecksumService;
import de.burger.forensics.adapters.joern.JoernCliSemanticAnalysisAdapter;
import de.burger.forensics.adapters.persistence.h2.H2AnalysisStoreAdapter;
import de.burger.forensics.adaptersupport.joern.JoernAnalysisConfig;
import de.burger.forensics.domain.port.out.SemanticAnalysisPort;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisException;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisRequest;
import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisRunner;
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
import java.nio.file.Path;
import java.util.Objects;

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
        try {
            semanticRunner().analyze(semanticRequest());
        } catch (ForensicsSemanticAnalysisException | IllegalArgumentException exception) {
            throw new GradleException(exception.getMessage(), exception);
        }
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

    protected ForensicsSemanticAnalysisRunner semanticRunner() {
        return new ForensicsSemanticAnalysisRunner(this::semanticAnalysisPort, this::analysisStore);
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

    private Path joernExecutable(String executableName) {
        return getJoernExecutables().getFiles().stream()
                .filter(file -> file.getName().equals(executableName) || file.getName().equals(executableName + ".bat"))
                .findFirst()
                .orElseGet(() -> new File(executableName))
                .toPath();
    }

    private ForensicsSemanticAnalysisRequest semanticRequest() {
        return new ForensicsSemanticAnalysisRequest(
                getJoernEnabled().getOrElse(false),
                joernExecutable("joern").toAbsolutePath().normalize(),
                joernExecutable("joern-parse").toAbsolutePath().normalize(),
                joernExecutable("joern-slice").toAbsolutePath().normalize(),
                getJoernWorkspaceDirectory().get().getAsFile().toPath().toAbsolutePath().normalize(),
                getJoernOutputDirectory().get().getAsFile().toPath().toAbsolutePath().normalize(),
                getJoernMaxHeap().getOrElse(""),
                getJoernTimeoutSeconds().getOrElse(300),
                getJoernFailOnError().getOrElse(true),
                getSourceRoots().getFiles().stream()
                        .map(file -> file.toPath().toAbsolutePath().normalize())
                        .toList(),
                getAnalysisStoreDirectory().get().getAsFile().toPath().toAbsolutePath().normalize(),
                getManifestFile().get().getAsFile().toPath().toAbsolutePath().normalize(),
                getChecksumsFile().get().getAsFile().toPath().toAbsolutePath().normalize(),
                getOutputFile().get().getAsFile().toPath().toAbsolutePath().normalize());
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
