/*
 * Root build.gradle.kts for the forensics_tracing Gradle plugin
 * - Uses versions from libs.versions.toml
 * - No Kotlin source/deps (Kotlin parsing removed)
 * - Ready for Gradle Plugin Portal + Sonatype (Nexus) publishing
 */

plugins {
    // Core
    `java-library`
    `java-gradle-plugin`
    // Publishing
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.nexus.publish)
}

group = providers.gradleProperty("POM_GROUP_ID").orNull ?: "de.burger.it"
version = providers.gradleProperty("POM_VERSION").orNull ?: "0.1.0"

// ------------------------------------------------------------------------------------
// Java toolchain & compilation
// ------------------------------------------------------------------------------------
java {
    // Use JDK 23 as preferred by the project context
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Enable modern warnings without breaking the build
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

repositories {
    mavenCentral()
}

// ------------------------------------------------------------------------------------
// Dependencies (from libs.versions.toml)
// ------------------------------------------------------------------------------------
dependencies {
    // Core analysis / parsing (JavaParser)
    implementation(libs.javaparser.symbol.solver.core)

    // Logging facade + backend
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.jul.to.slf4j)
    runtimeOnly(libs.jcl.over.slf4j)

    // AspectJ (if you use load-time weaving / agent elsewhere)
    implementation(libs.aspectj.rt)
    runtimeOnly(libs.aspectj.weaver)

    // --- Test ---
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("FAILED", "SKIPPED", "STANDARD_OUT", "STANDARD_ERROR")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

// ------------------------------------------------------------------------------------
// Gradle Plugin definition
// Adjust `id`, `implementationClass`, display fields as needed.
// ------------------------------------------------------------------------------------
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
        create("forensicsTracingPlugin").apply {
            id = providers.gradleProperty("PLUGIN_ID").orNull
                ?: "de.burger.it.forensics-tracing"
            implementationClass = providers.gradleProperty("PLUGIN_IMPL_CLASS").orNull
                ?: "de.burger.it.forensics.ForensicsTracingPlugin"

            displayName = "Forensics Tracing Gradle Plugin"
            description = providers.gradleProperty("POM_DESCRIPTION").orNull
                ?: "Forensics Tracing Gradle Plugin focusing on Java sources. Kotlin parsing removed."

            // Tags must be configured here (no pluginBundle in 2.x)
            this.tags.addAll("forensics", "tracing", "static-analysis", "java")
        }
    }
}

// ------------------------------------------------------------------------------------
// Maven Publishing (to Sonatype) via nexus-publish
// Configure credentials in environment or gradle.properties.
// ------------------------------------------------------------------------------------
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(providers.gradleProperty("POM_NAME").orNull ?: "forensics-tracing")
            description.set(
                providers.gradleProperty("POM_DESCRIPTION").orNull
                    ?: "Lightweight code forensics/tracing plugin for Java projects."
            )
            url.set(providers.gradleProperty("POM_URL").orNull ?: "https://github.com/burger-matthias/forensics_tracing")

            licenses {
                license {
                    name.set(providers.gradleProperty("POM_LICENSE_NAME").orNull ?: "Apache-2.0")
                    url.set(providers.gradleProperty("POM_LICENSE_URL").orNull
                        ?: "https://www.apache.org/licenses/LICENSE-2.0")
                    distribution.set("repo")
                }
            }

            developers {
                developer {
                    id.set(providers.gradleProperty("POM_DEVELOPER_ID").orNull ?: "mburger")
                    name.set(providers.gradleProperty("POM_DEVELOPER_NAME").orNull ?: "Matthias Burger")
                    email.set(providers.gradleProperty("POM_DEVELOPER_EMAIL").orNull ?: "dev@example.com")
                }
            }

            scm {
                connection.set(providers.gradleProperty("POM_SCM_CONNECTION").orNull
                    ?: "scm:git:https://github.com/burger-matthias/forensics_tracing.git")
                developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION").orNull
                    ?: "scm:git:ssh://git@github.com/burger-matthias/forensics_tracing.git")
                url.set(providers.gradleProperty("POM_SCM_URL").orNull
                    ?: "https://github.com/burger-matthias/forensics_tracing")
            }
        }
    }
}


// ------------------------------------------------------------------------------------
// Jar manifest (optional but useful)
// ------------------------------------------------------------------------------------
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "forensics-tracing",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "de.burger.it.forensics.tracing"
        )
    }
}

// ------------------------------------------------------------------------------------
// Quality gates (optional placeholders – add your tools if you like)
// ------------------------------------------------------------------------------------
// tasks.register("checkAll") {
//     group = "verification"
//     description = "Runs all verification tasks."
//     dependsOn("check")
// }
