package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class BtmGenPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Create extension
        BtmGenExtension ext = project.getExtensions()
                .create("forensicsBtmGen", BtmGenExtension.class);

        // Register a task with a STABILER NAME:
        project.getTasks().register("generateBtmRules", GenerateBtmTask.class, t -> {
            t.setGroup("forensics");
            t.setDescription("Generate Byteman rules into build/forensics");

            // Wire task inputs from extension
            t.getIncludeJava().convention(ext.includeJava);
            t.getUseAstScanner().convention(ext.useAstScanner);
            t.getHelperFqn().convention(ext.helperFqn);
            t.getEntryExit().convention(ext.entryExit);
            t.getMinBranchesPerMethod().convention(ext.minBranchesPerMethod);
            t.getLogToFile().convention(ext.logToFile);
            t.getLogFilePath().convention(ext.logFilePath);
            t.getOutputDir().convention(ext.outputDir);

            // Collect sources from srcDirs
            t.getSourceFiles().from(
                    ext.srcDirs.map(list -> list.stream().map(p ->
                            project.fileTree(p, cfg -> cfg.include("**/*.java","**/*.kt"))
                    ).toList())
            );
        });
    }
}
