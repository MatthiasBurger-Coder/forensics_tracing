pluginManagement {
    val forensicsPluginRoot = providers.gradleProperty("forensicsPluginRoot")
        .orElse("../..")
        .get()

    includeBuild(forensicsPluginRoot)

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "wildfly-forensics-gradle-config"
