package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

/**
 * Verifies that the semantic analysis task imported Joern artifacts into the analysis store.
 */
@DisableCachingByDefault(because = "The task validates local semantic enrichment artifacts.")
public abstract class ImportForensicsSemanticsTask extends DefaultTask {

    @Inject
    public ImportForensicsSemanticsTask() {
        conventionIfMissing(getJoernEnabled(), false);
        if (!getJoernOutputDirectory().isPresent()) {
            getJoernOutputDirectory().convention(getProjectLayout().getBuildDirectory().dir("forensics/joern"));
        }
    }

    @Input
    public abstract Property<@NotNull Boolean> getJoernEnabled();

    @OutputDirectory
    public abstract DirectoryProperty getJoernOutputDirectory();

    public void setExtension(BtmGenExtension extension) {
        getJoernEnabled().convention(extension.getJoernEnabled());
        getJoernOutputDirectory().convention(getProjectLayout().dir(extension.getJoernOutputDirectory()));
    }

    @TaskAction
    public void verifyImportedArtifacts() {
        if (!getJoernEnabled().getOrElse(false)) {
            throw new GradleException("Joern semantic import is disabled. Set btmGen.joernEnabled=true to run it.");
        }
        java.io.File outputDirectory = getJoernOutputDirectory().get().getAsFile();
        java.io.File callgraph = new java.io.File(outputDirectory, "callgraph.json");
        if (!callgraph.isFile()) {
            throw new GradleException("Joern semantic artifacts are missing. Run analyzeForensicsSemantics first.");
        }
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    private static <T> void conventionIfMissing(Property<T> property, T value) {
        if (!property.isPresent()) {
            property.convention(value);
        }
    }
}
