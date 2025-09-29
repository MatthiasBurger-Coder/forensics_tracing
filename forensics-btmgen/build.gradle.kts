import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    `java-library` apply false
    `java-gradle-plugin` apply false
    alias(libs.plugins.gradle.plugin.publish) apply false
    alias(libs.plugins.nexus.publish) apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
        }
    }
}
