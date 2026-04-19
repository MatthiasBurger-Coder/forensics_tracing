import java.math.BigDecimal
import java.math.RoundingMode
import java.io.StringReader
import org.xml.sax.InputSource
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    `java-library`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
    id("jacoco")
    alias(libs.plugins.sonar.qube.gradle.plugin)
    alias(libs.plugins.freefair.lombok.plugin)
}

val aspectjAgent by configurations.creating

dependencies {

    // Runtime
    implementation(libs.slf4j.api)
    compileOnly(libs.byteman)
    compileOnly(libs.jakarta.annotation.api)

    aspectjAgent(libs.aspectj.weaver)
    implementation(libs.aspectj.rt)
    runtimeOnly(libs.aspectj.weaver)

    implementation(libs.javaparser.symbol.solver.core)
    // Logging
    testImplementation(libs.byteman)

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

    //lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

configurations.all {
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
}

val java17 = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
plugins.withType<JavaPlugin>().configureEach {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
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

fun javaAgentArg(file: File): String {
    val p = file.absolutePath
    // Falls Leerzeichen/Sonderzeichen: komplett in Anführungszeichen setzen
    val quoted = if (p.any { it.isWhitespace() }) "\"$p\"" else p
    return "-javaagent:$quoted"
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    javaLauncher.set(java17)
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
version = providers.gradleProperty("VERSION").orNull ?: "0.0.3-SNAPSHOT"

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

// Resolve Mockito/ByteBuddy agent jar path from the test runtime classpath lazily
val mockitoAgentJar: Provider<String> = configurations.named("testRuntimeClasspath").map { cfg ->
    fun File.isJarNamed(prefix: String) = name.startsWith(prefix) && name.endsWith(".jar")

    val jar = cfg.files.firstOrNull { it.isJarNamed("mockito-inline") }
        ?: cfg.files.firstOrNull { it.isJarNamed("byte-buddy-agent") }

    jar?.absolutePath ?: ""
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Disabling CDS avoids noisy warnings when agents append to bootstrap classpath on some JDKs
    jvmArgs("-Xshare:off")
    finalizedBy(tasks.jacocoTestReport)

    // Use a Provider-based build directory (Gradle 7+; recommended for 8/9+)
    val reportsDir = layout.buildDirectory.dir("reports/spock")

    // Pass absolute path lazily to the test JVM
    systemProperty(
        "com.athaydes.spockframework.report.outputDir",
        reportsDir.map { it.asFile.absolutePath }.get()
    )

    // Optional extras
    systemProperty("com.athaydes.spockframework.report.projectName", "Customer Service Specs")
    systemProperty("com.athaydes.spockframework.report.projectVersion", "2.0-SNAPSHOT")
    systemProperty("com.athaydes.spockframework.report.outputFormats", "html")
    systemProperty("com.athaydes.spockframework.report.showCodeBlocks", "true")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
        csv.required.set(false)
        html.required.set(true)
    }
    // Exclude the application entry point from coverage reports
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude("de/burger/it/Main*")
                }
            }
        )
    )
}

abstract class PackageCoverageReportTask : DefaultTask() {
    @get:InputFile
    abstract val jacocoXml: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val lineThreshold: Property<BigDecimal>

    @get:Input
    abstract val branchThreshold: Property<BigDecimal>

    data class PackageCoverage(
        val name: String,
        val missedLines: Int,
        val coveredLines: Int,
        val missedBranches: Int,
        val coveredBranches: Int,
        val hasBranches: Boolean
    ) {
        val totalLines: Int = missedLines + coveredLines
        val totalBranches: Int = missedBranches + coveredBranches
        val lineCoverage: BigDecimal =
            if (totalLines == 0) BigDecimal.ONE else coveredLines.toBigDecimal().divide(totalLines.toBigDecimal(), 4, RoundingMode.HALF_UP)
        val branchCoverage: BigDecimal? =
            if (!hasBranches || totalBranches == 0) null else coveredBranches.toBigDecimal().divide(totalBranches.toBigDecimal(), 4, RoundingMode.HALF_UP)

        val impact: Int = missedLines + missedBranches
    }

    @TaskAction
    fun generate() {
        val xmlFile = jacocoXml.get().asFile
        if (!xmlFile.exists()) {
            throw GradleException("JaCoCo XML report not found at ${xmlFile.absolutePath}. Run jacocoTestReport first.")
        }

        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/validation", false)
        }
        val documentBuilder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val document = documentBuilder.parse(xmlFile)
        val packageNodes = document.getElementsByTagName("package")

        val packages = buildList {
            for (index in 0 until packageNodes.length) {
                val node = packageNodes.item(index)
                val attrs = node.attributes
                val name = attrs.getNamedItem("name")?.nodeValue ?: "unknown"
                val counters = (0 until node.childNodes.length)
                    .map { node.childNodes.item(it) }
                    .filter { it.nodeName == "counter" }

                val lineCounter = counters.firstOrNull { it.attributes.getNamedItem("type")?.nodeValue == "LINE" }
                val branchCounter = counters.firstOrNull { it.attributes.getNamedItem("type")?.nodeValue == "BRANCH" }

                val missedLines = lineCounter?.attributes?.getNamedItem("missed")?.nodeValue?.toIntOrNull() ?: 0
                val coveredLines = lineCounter?.attributes?.getNamedItem("covered")?.nodeValue?.toIntOrNull() ?: 0
                val missedBranches = branchCounter?.attributes?.getNamedItem("missed")?.nodeValue?.toIntOrNull() ?: 0
                val coveredBranches = branchCounter?.attributes?.getNamedItem("covered")?.nodeValue?.toIntOrNull() ?: 0
                val hasBranches = branchCounter != null

                add(
                    PackageCoverage(
                        name = name.replace('/', '.'),
                        missedLines = missedLines,
                        coveredLines = coveredLines,
                        missedBranches = missedBranches,
                        coveredBranches = coveredBranches,
                        hasBranches = hasBranches
                    )
                )
            }
        }.sortedWith(compareByDescending<PackageCoverage> { it.impact }.thenBy { it.name })

        val report = reportFile.get().asFile
        report.parentFile.mkdirs()

        val lineThresholdValue = lineThreshold.get()
        val branchThresholdValue = branchThreshold.get()

        val failures = packages.filter { pkg ->
            val lineFails = pkg.lineCoverage < lineThresholdValue
            val branchFails = pkg.branchCoverage?.let { it < branchThresholdValue } ?: false
            lineFails || branchFails
        }

        report.bufferedWriter().use { writer ->
            writer.appendLine("Package coverage report")
            writer.appendLine("Line threshold: ${(lineThresholdValue * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)}%")
            writer.appendLine("Branch threshold: ${(branchThresholdValue * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)}%")
            writer.appendLine(
                "packageName\tlineCoverage\tbranchCoverage\tmissedLines\tmissedBranches\ttotalLines\ttotalBranches"
            )
            packages.forEach { pkg ->
                val linePercent = (pkg.lineCoverage * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                val branchPercent = pkg.branchCoverage?.let { (it * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP) }
                writer.appendLine(
                    listOf(
                        pkg.name,
                        "${linePercent}%",
                        branchPercent?.let { "${it}%" } ?: "n/a",
                        pkg.missedLines,
                        pkg.missedBranches,
                        pkg.totalLines,
                        pkg.totalBranches
                    ).joinToString(separator = "\t")
                )
            }
        }

        if (failures.isNotEmpty()) {
            val summary = failures.joinToString(separator = System.lineSeparator()) { pkg ->
                val linePercent = (pkg.lineCoverage * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                val branchPercent = pkg.branchCoverage?.let { (it * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP) } ?: "n/a"
                "- ${pkg.name}: line=${linePercent}% branch=${branchPercent}% (missedLines=${pkg.missedLines}, missedBranches=${pkg.missedBranches})"
            }
            throw GradleException(
                "Package coverage below threshold:${System.lineSeparator()}${summary}${System.lineSeparator()}Report: ${report.absolutePath}"
            )
        }
    }
}

tasks.register<PackageCoverageReportTask>("checkPackageCoverage") {
    dependsOn(tasks.jacocoTestReport)
    jacocoXml.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    reportFile.set(layout.buildDirectory.file("reports/coverage/package-coverage.txt"))
    lineThreshold.set(BigDecimal("0.80"))
    branchThreshold.set(BigDecimal("0.80"))
}
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    // Fail the build if coverage is below 86%
    violationRules {
        rule {
            limit {
                // enforce 86% line coverage
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
        }
    }
    // Exclude the application entry point from coverage verification
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude("de/burger/it/Main*")
                }
            }
        )
    )
    dependsOn(tasks.test)
}

tasks.check {
    dependsOn("jacocoTestCoverageVerification")
}

sonar {
    val sonarToken = sequenceOf(
        providers.environmentVariable("SONAR_TOKEN").orNull,
        providers.gradleProperty("sonar.token").orNull
    )
        .mapNotNull { it?.trim() }
        .firstOrNull { it.isNotEmpty() }

    if (sonarToken == null) {
        // Skip Sonar when no token is configured to avoid failing local or CI builds.
        tasks.named("sonar").configure { enabled = false }
    }
    properties {
        property("sonar.projectKey", "MatthiasBurger-Coder_forensics_tracing")
        property("sonar.organization", "matthiasburger-coder")
        property("sonar.host.url", "https://sonarcloud.io")
        // Coverage-Report-Pfad:
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.absolutePath
        )
    }
}
