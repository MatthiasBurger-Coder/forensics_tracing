package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the extension and the generateBtmRules task.
 * Defaults are applied safely during task registration (no Provider lambdas).
 */
public class BtmGenPlugin implements Plugin<@NotNull Project> {
    @Override
    public void apply(Project project) {
        // Create extension
        BtmGenExtension ext = project.getExtensions().create("btmGen", BtmGenExtension.class);

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
                        t.getOutputFile().fileValue(ext.getOutputFile().get());
                    } else {
                        t.getOutputFile().convention(
                                project.getLayout().getBuildDirectory().file("forensics/forensics.btm")
                        );
                    }
                }
        );

        // Optional: hook into build lifecycle
        project.getTasks().matching(it -> it.getName().equals("build"))
                .configureEach(it -> it.dependsOn(task));
    }
}
