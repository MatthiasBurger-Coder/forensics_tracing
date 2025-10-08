plugins {
    `java-library`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
}

val aspectjAgent by configurations.creating

dependencies {

    // Runtime
    compileOnly(libs.slf4j.api)
    compileOnly(libs.byteman)
    compileOnly(libs.jakarta.annotation.api)

    aspectjAgent(libs.aspectj.weaver)
    implementation(libs.aspectj.rt)
    runtimeOnly(libs.aspectj.weaver)

    implementation(libs.javaparser.symbol.solver.core)

    // Logging
    testImplementation(libs.slf4j.api)

    // AspectJ
    testRuntimeOnly(libs.aspectj.weaver)
    testImplementation(libs.aspectj.weaver)

    // Junit 5
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)

    testImplementation(libs.assertj.core)

    // Mockito
    testImplementation(platform(libs.mockito.bom))
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)

    // Arch-unit
    testImplementation(libs.archunit.junit)

    //Byte Man
    testImplementation(libs.byte.buddy.agent)

    testImplementation(gradleTestKit())
}

configurations.all {
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
}

val java21 = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
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
    enabled = false
}

val testLogFile = layout.buildDirectory.file("test-logs/forensics-btmgen.log")

fun javaAgentArg(file: java.io.File): String {
    val p = file.absolutePath
    // Falls Leerzeichen/Sonderzeichen: komplett in Anführungszeichen setzen
    val quoted = if (p.any { it.isWhitespace() }) "\"$p\"" else p
    return "-javaagent:$quoted"
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    javaLauncher.set(java21)
    jvmArgumentProviders += CommandLineArgumentProvider {
        val weaverJar = aspectjAgent.resolve().firstOrNull { it.name.startsWith("aspectjweaver") }
            ?: throw GradleException("aspectjweaver*.jar nicht gefunden. Füge 'aspectjAgent(libs.aspectj.weaver)' hinzu.")
        val arg = javaAgentArg(weaverJar)
        listOf(
            arg,
            "-XX:+PrintCommandLineFlags"
        )
    }

    maxParallelForks = 1
    testLogging {
        events("FAILED", "SKIPPED")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true // zeigt Aspect-Logs in der Test-Konsole
    }
}

tasks.test {
    useJUnitPlatform()

    doFirst {
        // Log-Ordner sicherstellen
        testLogFile.get().asFile.parentFile.mkdirs()

        // Aspect-File-Mirror
        systemProperty("forensics.btmgen.logToFile", "true")
        systemProperty("forensics.btmgen.logFile", testLogFile.get().asFile.absolutePath)

        jvmArgs(
            "-Xshare:off" // ok, optional
        )

        // Weniger Weaver-Output
        systemProperty("org.aspectj.weaver.showWeaveInfo", "false")
        systemProperty("aj.weaving.verbose", "false")

        // Explizit die aop.xml aus main laden (nur 1x vorhanden lassen)
        systemProperty("org.aspectj.weaver.loadtime.configuration", "classpath:META-INF/aop.xml")

        // Dein eigener Schalter
        systemProperty("forensics.aspect.enabled", "true")
    }

    // Test-Logging
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
version = providers.gradleProperty("VERSION").orNull ?: "0.0.2-SNAPSHOT"

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
            // Point to the actual implementation class in the project
            implementationClass = providers.gradleProperty("PLUGIN_IMPL_CLASS").orNull
                ?: "de.burger.forensics.plugin.btmgen.gradle.BtmGenPlugin"

            displayName = "Forensics BTM Generator Gradle Plugin"
            description = providers.gradleProperty("POM_DESCRIPTION").orNull
                ?: "Generates Byteman tracing rules from Java sources."

            // Tags must be configured here (no pluginBundle in 2.x)
            val pluginTags: List<String> = listOf("forensics", "tracing", "byteman", "java")
            tags.set(pluginTags)
        }
    }
}