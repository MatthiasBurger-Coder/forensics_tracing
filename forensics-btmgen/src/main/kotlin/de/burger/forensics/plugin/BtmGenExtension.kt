package de.burger.forensics.plugin

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import javax.inject.Inject

abstract class BtmGenExtension @Inject constructor(
    objects: ObjectFactory,
    layout: ProjectLayout
) {
    val srcDirs: ListProperty<String> = objects.listProperty(String::class.java)
    val pkgPrefix: Property<String> = objects.property(String::class.java)
    val helperFqn: Property<String> = objects.property(String::class.java)
    val entryExit: Property<Boolean> = objects.property(Boolean::class.java)
    val trackedVars: ListProperty<String> = objects.listProperty(String::class.java)
    val includeJava: Property<Boolean> = objects.property(Boolean::class.java)
    val outputDir: DirectoryProperty = objects.directoryProperty()

    init {
        srcDirs.convention(listOf("src/main/kotlin"))
        pkgPrefix.convention("")
        helperFqn.convention("de.burger.forensics.ForensicsHelper")
        entryExit.convention(true)
        trackedVars.convention(emptyList())
        includeJava.convention(false)
        outputDir.convention(layout.buildDirectory.dir("forensics"))
    }
}
