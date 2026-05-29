package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public final class BtmGenPlugin implements Plugin<Project> {
    private static final String FORENSICS_GROUP = "forensics";

    @Override
    public void apply(Project project) {
        BtmGenExtension extension = project.getExtensions()
                .create("forensicsTracing", BtmGenExtension.class);

        TaskProvider<SubmitForensicsAnalysisTask> taskProvider = project.getTasks().register(
                "submitForensicsAnalysis",
                SubmitForensicsAnalysisTask.class,
                task -> {
                    task.setGroup(FORENSICS_GROUP);
                    task.setDescription("Submits this Gradle build context to the Forensics Analytics server over gRPC.");
                    task.configureFrom(project, extension);
                }
        );

        project.getTasks().register("forensicsAnalyze", task -> {
            task.setGroup(FORENSICS_GROUP);
            task.setDescription("Submits this Gradle build context to the Forensics Analytics server.");
            task.dependsOn(taskProvider);
        });
    }
}
