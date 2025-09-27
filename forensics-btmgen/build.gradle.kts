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
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(kotlin("test-junit5"))
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
}

