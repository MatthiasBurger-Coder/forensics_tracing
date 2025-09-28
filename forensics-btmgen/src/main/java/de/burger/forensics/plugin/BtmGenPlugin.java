// DEST: src/main/java/de/burger/forensics/plugin/BtmGenPlugin.java
package de.burger.forensics.plugin;

import java.io.File;
import java.io.IOException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

public class BtmGenPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull Project project) {
        BtmGenExtension ext = project.getExtensions().create(
                "forensicsBtmGen",
                BtmGenExtension.class,
                project.getObjects(),
                project.getLayout()
        );

        project.afterEvaluate(p -> {
            boolean enableFile = ext.getLogToFile().getOrElse(true);
            String relativePath = ext.getLogFilePath().getOrNull();
            if (relativePath == null || relativePath.isBlank()) {
                relativePath = "logs/forensics-btmgen.log";
            }
            if (enableFile) {
                File file = new File(project.getProjectDir(), relativePath);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                    } catch (IOException ignored) {
                        // Ignore failures to create the log file.
                    }
                }
            }
            if (System.getProperty("forensics.btmgen.logToFile") == null) {
                System.setProperty("forensics.btmgen.logToFile", Boolean.toString(enableFile));
            }
            if (System.getProperty("forensics.btmgen.logFile") == null) {
                System.setProperty("forensics.btmgen.logFile", new File(project.getProjectDir(), relativePath).getPath());
            }
        });
    }
}
