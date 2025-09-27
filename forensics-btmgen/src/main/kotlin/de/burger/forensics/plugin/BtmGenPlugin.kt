package de.burger.forensics.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

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

        // Propagate configured log level to a system property so Log4j2 (if present) can pick it up.
        project.afterEvaluate {
            val level = ext.logLevel.orNull?.trim()?.uppercase().takeUnless { it.isNullOrBlank() } ?: "ERROR"
            System.setProperty("forensics.btmgen.logLevel", level)
        }
    }
}
