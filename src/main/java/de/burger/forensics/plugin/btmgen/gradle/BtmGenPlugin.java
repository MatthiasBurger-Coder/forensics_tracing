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

public final class BtmGenPlugin implements Plugin<@NotNull Project> {
    private static final String FORENSICS_GROUP = "forensics";
    private static final String FORENSICS_DIR = "forensics";
    private static final String RUNTIME_HELPER_ATTACHED_MARKER = "de.burger.forensics.btmgen.runtimeHelperAttached";

    @Override
    public void apply(Project project) {
        final BtmGenExtension ext = project.getExtensions()
                .create("btmGen", BtmGenExtension.class);

        // Register task
        TaskProvider<@NotNull GenerateBtmTask> taskProvider = project.getTasks().register(
                "generateBtmRules",
                GenerateBtmTask.class,
                t -> {
                    t.setGroup(FORENSICS_GROUP);
                    t.setDescription("Generates Byteman (.btm) rules by scanning Java sources.");
                    t.setExtension(ext);
                }
        );
        project.getTasks().register(
                "generateActivityPumlFromBtm",
                GenerateActivityPumlFromBtmTask.class,
                t -> {
                    t.setGroup(FORENSICS_GROUP);
                    t.setDescription("Converts a Byteman (.btm) file into renderable PlantUML activity diagrams.");
                    t.getInputBtm().convention(project.getLayout().getBuildDirectory().file(FORENSICS_DIR + "/forensics.btm"));
                    t.getOutputPuml().convention(project.getLayout().getBuildDirectory().file(FORENSICS_DIR + "/forensics-activity.puml"));
                    t.getDiagramTitle().convention("Forensics Activity from Byteman Rules");
                }
        );
        project.getTasks().register(
                "generateActivityPumlFromTrace",
                GenerateActivityPumlFromTraceTask.class,
                t -> {
                    t.setGroup(FORENSICS_GROUP);
                    t.setDescription("Converts runtime trace JSON (METHOD_EXIT/BRANCH_TAKEN) into a PlantUML activity diagram.");
                    t.getInputTrace().convention(project.getLayout().getProjectDirectory().file("logs/trace.json"));
                    t.getOutputPuml().convention(project.getLayout().getBuildDirectory().file(FORENSICS_DIR + "/trace-activity.puml"));
                }
        );
        taskProvider.configure(task -> {
            task.getSourceRoot().finalizeValueOnRead();
            task.getOutputFile().finalizeValueOnRead();
        });

        project.getPlugins().withType(JavaPlugin.class, ignored -> attachRuntimeHelper(project));
        configureSubprojectRuntimeHelpers(project, ext);

        project.getTasks().matching(t -> t.getName().equals("build"))
                .configureEach(t -> t.dependsOn(taskProvider));
    }

    private void configureSubprojectRuntimeHelpers(Project project, BtmGenExtension ext) {
        project.getSubprojects().forEach(subproject ->
                subproject.getPlugins().withType(JavaPlugin.class, ignored -> {
                    if (ext.getScanSubprojects().getOrElse(false)) {
                        attachRuntimeHelper(subproject);
                    }
                })
        );

        project.getGradle().projectsEvaluated(ignored -> {
            if (!ext.getScanSubprojects().getOrElse(false)) {
                return;
            }
            project.getSubprojects().forEach(subproject -> {
                if (subproject.getPlugins().hasPlugin("java") || subproject.getPlugins().hasPlugin("java-library")) {
                    attachRuntimeHelper(subproject);
                }
            });
        });
    }

    private void attachRuntimeHelper(Project project) {
        if (runtimeHelperAlreadyAttached(project)) {
            return;
        }

        Optional<File> runtimeArtifact = PluginRuntimeLocator.locateFor(BtmGenPlugin.class);
        if (runtimeArtifact.isEmpty()) {
            project.getLogger().warn("forensics-btmgen: Unable to locate runtime helper artifact; helper will not be added to classpath.");
            return;
        }

        FileCollection helperFiles = project.files(runtimeArtifact.get());
        boolean attached = addDependencyIfPresent(project, "runtimeOnly", helperFiles);
        attached = addDependencyIfPresent(project, "testRuntimeOnly", helperFiles) || attached;
        if (attached) {
            project.getExtensions().getExtraProperties().set(RUNTIME_HELPER_ATTACHED_MARKER, true);
        }
    }

    private boolean addDependencyIfPresent(Project project, String configurationName, FileCollection files) {
        if (project.getConfigurations().findByName(configurationName) != null) {
            project.getDependencies().add(configurationName, files);
            return true;
        }
        return false;
    }

    private boolean runtimeHelperAlreadyAttached(Project project) {
        return project.getExtensions().getExtraProperties().has(RUNTIME_HELPER_ATTACHED_MARKER);
    }
}
