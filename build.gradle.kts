import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.aspectj.rt)
    runtimeOnly(libs.aspectj.weaver)
    implementation(libs.javaparser.symbol.solver.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.aspectj.weaver)
    testImplementation(libs.byte.buddy.agent)
    testImplementation(libs.assertj.core)
    testImplementation(platform(libs.mockito.bom))
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

plugins.withType<JavaPlugin>().configureEach {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all"))
    }
}

// Relax Javadoc doclint to avoid failing the build on strict checks
tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    val opts = options as StandardJavadocDocletOptions
    // Disable all doclint checks and suppress warnings output
    opts.addBooleanOption("Xdoclint:none", true)
    opts.addBooleanOption("quiet", true)
    // Do not fail the build if Javadoc encounters warnings
    isFailOnError = false
}

tasks.test {
    useJUnitPlatform()

    // Mockito als Java-Agent konfigurieren, um Warnungen zu vermeiden
    val byteBuddyAgent = configurations.testRuntimeClasspath.get()
        .firstOrNull { it.name.contains("byte-buddy-agent") }

    if (byteBuddyAgent != null) {
        jvmArgs(
            "-javaagent:${byteBuddyAgent.absolutePath}",
            "-Xshare:off"  // Deaktiviert Class Data Sharing, um die Warnung zu unterdrücken
        )
    } else {
        jvmArgs("-Xshare:off")
    }

    testLogging {
        events("FAILED", "SKIPPED", "STANDARD_OUT", "STANDARD_ERROR")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

configurations.named("compileClasspath") {
    exclude(group = "ch.qos.logback", module = "logback-classic")
}
configurations.named("runtimeClasspath") {
    exclude(group = "ch.qos.logback", module = "logback-classic")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "forensics-tracing",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "de.burger.it.forensics.tracing"
        )
    }
}



// Project coordinates for publishing
// Use Gradle properties if provided, otherwise fall back to sensible defaults
group = providers.gradleProperty("GROUP").orNull ?: "de.burger.forensics"
version = providers.gradleProperty("VERSION").orNull ?: "1.0.0-SNAPSHOT"
