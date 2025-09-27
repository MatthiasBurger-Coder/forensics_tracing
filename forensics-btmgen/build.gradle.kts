plugins {
    id("java")
    id("java-gradle-plugin")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
}

group = "de.burger.forensics"
version = "1.0.0"

description = "Generates Byteman tracing rules from Java sources"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

gradlePlugin {
    plugins {
        register("btmgen") {
            id = "de.burger.forensics.btmgen"
            implementationClass = "de.burger.forensics.plugin.BtmGenPlugin"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.javaparser.symbol.solver.core)

    // Logging facade and bridges
    implementation(libs.slf4j.api)
    implementation(libs.jul.to.slf4j)
    implementation(libs.jcl.over.slf4j)

    // Use Gradle's logging backend during plugin execution.
    // Do NOT add any SLF4J provider/binding to main configurations to avoid multiple providers on classpath.
    // For tests we provide a Log4j2 binding so log4j2.xml is honored.

    // AspectJ runtime/weaver
    implementation(libs.aspectj.rt)
    runtimeOnly(libs.aspectj.weaver)

    // For tests: allow self-attachment to obtain Instrumentation when -javaagent is unavailable
    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.13")
    testImplementation(libs.aspectj.weaver)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation(libs.assertj.core)
    testImplementation(kotlin("test-junit5"))
    testImplementation(gradleTestKit())

    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Ensure aspect file logging is enabled and points to the standard log file
    doFirst {
        systemProperty("forensics.btmgen.logToFile", "true")
        systemProperty("forensics.btmgen.logFile", "logs/forensics-btmgen.log")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("forensics-btmgen")
                description.set(project.description)
                url.set("https://example.com/forensics-btmgen")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("burger")
                        name.set("Burger Forensics Team")
                        email.set("info@example.com")
                    }
                }
                scm {
                    url.set("https://github.com/example/forensics-btmgen")
                    connection.set("scm:git:https://github.com/example/forensics-btmgen.git")
                    developerConnection.set("scm:git:ssh://git@github.com/example/forensics-btmgen.git")
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

