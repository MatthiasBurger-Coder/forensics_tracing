package de.burger.forensics.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class BtmGenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Expose extension only; do not register tasks automatically.
        // Users should register GenerateBtmTask themselves and wire it to this extension as needed.
        project.extensions.create(
            "forensicsBtmGen",
            BtmGenExtension::class.java,
            project.objects,
            project.layout
        )
    }
}
