package de.burger.forensics.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class BtmGenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Expose extension only; do not register tasks automatically.
        // Users should register GenerateBtmTask themselves and wire it to this extension as needed.
        val ext = project.extensions.create(
            "forensicsBtmGen",
            BtmGenExtension::class.java,
            project.objects,
            project.layout
        )

        // Ensure a default logfile exists so users can immediately find it,
        // and wire system properties so MethodLoggingAspect (if woven) writes to the same path.
        project.afterEvaluate {
            val enableFile = ext.logToFile.orNull ?: true
            val relativePath = ext.logFilePath.orNull?.takeIf { it.isNotBlank() } ?: "logs/forensics-btmgen.log"
            if (enableFile) {
                val file = File(project.projectDir, relativePath)
                file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                if (!file.exists()) runCatching { file.createNewFile() }
            }
            // Propagate to system properties for the Aspect to pick up regardless of SLF4J backend
            if (System.getProperty("forensics.btmgen.logToFile") == null) {
                System.setProperty("forensics.btmgen.logToFile", enableFile.toString())
            }
            if (System.getProperty("forensics.btmgen.logFile") == null) {
                System.setProperty("forensics.btmgen.logFile", File(project.projectDir, relativePath).path)
            }
        }
    }
}
