package de.burger.forensics.plugin;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

/**
 * Gradle extension exposing configuration knobs for the generator task.
 */
public abstract class BtmGenExtension {

    private final ListProperty<String> srcDirs;
    private final ListProperty<String> packagePrefixes;
    private final Property<String> helperFqn;
    private final Property<Boolean> safeMode;
    private final Property<Boolean> includeEntryExit;
    private final Property<Boolean> includeTimestamp;
    private final Property<Integer> minBranchesPerMethod;
    private final DirectoryProperty outputDirectory;

    @Inject
    public BtmGenExtension(@NotNull ObjectFactory objects, @NotNull ProjectLayout layout) {
        this.srcDirs = objects.listProperty(String.class);
        this.packagePrefixes = objects.listProperty(String.class);
        this.helperFqn = objects.property(String.class);
        this.safeMode = objects.property(Boolean.class);
        this.includeEntryExit = objects.property(Boolean.class);
        this.includeTimestamp = objects.property(Boolean.class);
        this.minBranchesPerMethod = objects.property(Integer.class);
        this.outputDirectory = objects.directoryProperty();

        this.srcDirs.convention(Collections.singletonList("src/main/java"));
        this.packagePrefixes.convention(Collections.emptyList());
        this.helperFqn.convention("org.example.trace.SafeEval");
        this.safeMode.convention(true);
        this.includeEntryExit.convention(true);
        this.includeTimestamp.convention(true);
        this.minBranchesPerMethod.convention(0);
        this.outputDirectory.convention(layout.getBuildDirectory().dir("forensics"));
    }

    public ListProperty<String> getSrcDirs() {
        return srcDirs;
    }

    public ListProperty<String> getPackagePrefixes() {
        return packagePrefixes;
    }

    public Property<String> getHelperFqn() {
        return helperFqn;
    }

    public Property<Boolean> getSafeMode() {
        return safeMode;
    }

    public Property<Boolean> getIncludeEntryExit() {
        return includeEntryExit;
    }

    public Property<Boolean> getIncludeTimestamp() {
        return includeTimestamp;
    }

    public Property<Integer> getMinBranchesPerMethod() {
        return minBranchesPerMethod;
    }

    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }
}
