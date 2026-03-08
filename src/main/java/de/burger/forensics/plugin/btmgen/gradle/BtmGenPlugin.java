package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.gradle.internal.PluginRuntimeLocator;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Optional;

/**
 * Compatible wiring when the extension exposes Property<File> rather than DirectoryProperty/RegularFileProperty.
 * We map File -> Directory/RegularFile Providers without reading values eagerly.
 */
public final class BtmGenPlugin implements Plugin<@NotNull Project> {

    @Override
    public void apply(Project project) {
        final BtmGenExtension ext = project.getExtensions()
                .create("btmGen", BtmGenExtension.class);

        // Register task
        TaskProvider<@NotNull GenerateBtmTask> taskProvider = project.getTasks().register(
                "generateBtmRules",
                GenerateBtmTask.class,
                t -> {
                    t.setGroup("forensics");
                    t.setDescription("Generates Byteman (.btm) rules by scanning Java sources.");
                    t.setExtension(ext);

                    // --- Conventions/Defaults ---
                    // If extension provides explicit values -> set them directly (no providers)
                    if (ext.getSourceRoot().isPresent()) {
                        t.getSourceRoot().set(ext.getSourceRoot().get());
                    } else {
                        // Otherwise use a safe default provider from the layout
                        t.getSourceRoot().convention(
                                project.getLayout().getProjectDirectory().dir("src/main/java")
                        );
                    }

                    if (ext.getOutputFile().isPresent()) {
                        var file = ext.getOutputFile().get();
                        t.getOutputFile().fileValue(file);
                        if (file.getParentFile() != null) {
                            t.getOutputDir().fileValue(file.getParentFile());
                        }
                    } else {
                        t.getOutputFile().convention(
                                project.getLayout().getBuildDirectory().file("forensics/forensics.btm")
                        );
                    }

                    if (!t.getOutputDir().isPresent()) {
                        t.getOutputDir().convention(
                                project.getLayout().getBuildDirectory().dir("forensics")
                        );
                    }
                }
        );
        project.getTasks().register(
                "generateActivityPumlFromBtm",
                GenerateActivityPumlFromBtmTask.class,
                t -> {
                    t.setGroup("forensics");
                    t.setDescription("Converts a Byteman (.btm) file into a PlantUML activity diagram with swimlanes.");
                    t.getInputBtm().convention(project.getLayout().getBuildDirectory().file("forensics/forensics.btm"));
                    t.getOutputPuml().convention(project.getLayout().getBuildDirectory().file("forensics/forensics-activity.puml"));
                    t.getDiagramTitle().convention("Forensics Activity from Byteman Rules");
                }
        );
        project.getTasks().register(
                "generateActivityPumlFromTrace",
                GenerateActivityPumlFromTraceTask.class,
                t -> {
                    t.setGroup("forensics");
                    t.setDescription("Converts runtime trace JSON (METHOD_EXIT/BRANCH_TAKEN) into a PlantUML activity diagram.");
                    t.getInputTrace().convention(project.getLayout().getProjectDirectory().file("logs/trace.json"));
                    t.getOutputPuml().convention(project.getLayout().getBuildDirectory().file("forensics/trace-activity.puml"));
                }
        );
        taskProvider.configure(task -> {
            task.getSourceRoot().finalizeValueOnRead();
            task.getOutputFile().finalizeValueOnRead();
        });

        project.getPlugins().withType(JavaPlugin.class, ignored -> attachRuntimeHelper(project));

        project.getTasks().matching(t -> t.getName().equals("build"))
                .configureEach(t -> t.dependsOn(taskProvider));
    }

    private void attachRuntimeHelper(Project project) {
        Optional<File> runtimeArtifact = PluginRuntimeLocator.locateFor(BtmGenPlugin.class);
        if (runtimeArtifact.isEmpty()) {
            project.getLogger().warn("forensics-btmgen: Unable to locate runtime helper artifact; helper will not be added to classpath.");
            return;
        }

        FileCollection helperFiles = project.files(runtimeArtifact.get());
        addDependencyIfPresent(project, "runtimeOnly", helperFiles);
        addDependencyIfPresent(project, "testRuntimeOnly", helperFiles);
    }

    private void addDependencyIfPresent(Project project, String configurationName, FileCollection files) {
        if (project.getConfigurations().findByName(configurationName) != null) {
            project.getDependencies().add(configurationName, files);
        }
    }
}
