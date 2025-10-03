package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.Directory;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Compatible wiring when the extension exposes Property<File> rather than DirectoryProperty/RegularFileProperty.
 * We map File -> Directory/RegularFile Providers without reading values eagerly.
 */
public final class BtmGenPlugin implements Plugin<@NotNull Project> {

    @Override
    public void apply(Project project) {
        final BtmGenLegacyFileExtension ext = project.getExtensions()
                .create("btmGen", BtmGenLegacyFileExtension.class);

        // Register task
        TaskProvider<@NotNull GenerateBtmTask> task = project.getTasks().register(
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

                    task.getSourceRoot().finalizeValueOnRead();
                    task.getOutputFile().finalizeValueOnRead();
                });

        project.getTasks().matching(t -> t.getName().equals("build"))
                .configureEach(t -> t.dependsOn(taskProvider));
    }
}
