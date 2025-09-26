package de.burger.forensics.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class BtmGenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "forensicsBtmGen",
            BtmGenExtension::class.java,
            project.objects,
            project.layout
        )

        project.tasks.register("generateBtmRules", GenerateBtmTask::class.java) { task ->
            task.group = "forensics"
            task.description = "Generates Byteman tracing rules into build/forensics/tracing.btm"
            task.srcDirs.set(extension.srcDirs)
            task.packagePrefix.set(extension.pkgPrefix)
            task.helperFqn.set(extension.helperFqn)
            task.entryExit.set(extension.entryExit)
            task.trackedVars.set(extension.trackedVars)
            task.includeJava.set(extension.includeJava)
            task.includeTimestamp.set(extension.includeTimestamp)
            task.outputDir.set(extension.outputDir)
            task.maxStringLength.set(extension.maxStringLength)
        }
    }
}
