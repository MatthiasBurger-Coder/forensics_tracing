import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
}

val aspectjAgent by configurations.creating

dependencies {
    implementation(libs.slf4j.api)
    aspectjAgent(libs.aspectj.weaver)
    implementation(libs.aspectj.rt)
    runtimeOnly(libs.aspectj.weaver)
    implementation(libs.javaparser.symbol.solver.core)

    testRuntimeOnly(libs.aspectj.weaver)
    testRuntimeOnly(libs.logback.classic)
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

    testImplementation(gradleTestKit())
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

val testLogFile = layout.buildDirectory.file("test-logs/forensics-btmgen.log")

tasks.test {
    useJUnitPlatform()

    doFirst {
        testLogFile.get().asFile.parentFile.mkdirs()
//        val weaver = configurations.testRuntimeClasspath.get().files
//            .find { it.name.startsWith("aspectjweaver") }
//            ?: throw GradleException(
//                "aspectjweaver JAR not found on testRuntimeClasspath. " +
//                        "Add testRuntimeOnly(libs.aspectj.weaver) to dependencies."
//            )
//
//        jvmArgs("-javaagent=${weaver.absolutePath}")
    }

    systemProperty("forensics.btmgen.logToFile", "true")
    systemProperty("forensics.btmgen.logFile", testLogFile.get().asFile.absolutePath)

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

gradlePlugin {
    // Top-level metadata for the plugin bundle
    website.set(
        providers.gradleProperty("POM_URL").orNull
            ?: "https://github.com/burger-matthias/forensics_tracing"
    )
    vcsUrl.set(
        providers.gradleProperty("POM_SCM_URL").orNull
            ?: "https://github.com/burger-matthias/forensics_tracing.git"
    )
    plugins {
        create("btmGenPlugin").apply {
            // This ID must match what tests use
            id = providers.gradleProperty("PLUGIN_ID").orNull
                ?: "de.burger.forensics.btmgen"
            // Point to actual implementation class in the project
            implementationClass = providers.gradleProperty("PLUGIN_IMPL_CLASS").orNull
                ?: "de.burger.forensics.plugin.BtmGenPlugin"

            displayName = "Forensics BTM Generator Gradle Plugin"
            description = providers.gradleProperty("POM_DESCRIPTION").orNull
                ?: "Generates Byteman tracing rules from Java sources."

            // Tags must be configured here (no pluginBundle in 2.x)
            val pluginTags: List<String> = listOf("forensics", "tracing", "byteman", "java")
            tags.set(pluginTags)
        }
    }
}