import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.23"
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.2.1"
    id("maven-publish")
    id("signing")
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

group = "de.burger.forensics"
version = "1.0.0"

description = "Generates Byteman tracing rules from Kotlin sources"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

val functionalTest by sourceSets.creating

configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations["testImplementation"])
configurations[functionalTest.runtimeOnlyConfigurationName].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.23")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    add("functionalTestImplementation", kotlin("test"))
    add("functionalTestImplementation", "org.junit.jupiter:junit-jupiter-api:5.10.2")
    add("functionalTestImplementation", gradleTestKit())
    add("functionalTestRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.10.2")
}


sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
        resources.srcDir("src/main/resources")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    website.set("https://example.com/forensics-btmgen")
    vcsUrl.set("https://github.com/example/forensics-btmgen")
    plugins {
        create("btmgen") {
            id = "de.burger.forensics.btmgen"
            implementationClass = "de.burger.forensics.plugin.BtmGenPlugin"
            displayName = "Forensics Byteman Rule Generator"
            description = "Generates Byteman tracing rules for call chains and decisions"
            tags.set(listOf("forensics", "byteman", "tracing", "kotlin"))
        }
    }
    testSourceSets(functionalTest)
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

signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

tasks.register<Test>("functionalTest") {
    description = "Runs the functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(tasks.named("functionalTest"))
}

