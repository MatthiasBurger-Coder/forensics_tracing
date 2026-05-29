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
    alias(libs.plugins.freefair.lombok.plugin)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.maven.plugin.development)
    alias(libs.plugins.sonar.qube.gradle.plugin)
}

group = providers.gradleProperty("GROUP").orNull ?: "de.burger.forensics"
version = providers.gradleProperty("VERSION").orNull ?: "0.0.3-SNAPSHOT"

val aspectjAgent by configurations.creating
val mavenPluginDescriptorDependencies by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Runtime dependencies written to the generated Maven plugin descriptor."
}

dependencies {
    aspectjAgent(libs.aspectj.weaver)

    implementation(libs.slf4j.api)
    implementation(libs.aspectj.rt)
    implementation(libs.javaparser.symbol.solver.core)
    implementation(libs.h2)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.java)

    compileOnly(libs.byteman)
    compileOnly(libs.jakarta.annotation.api)
    compileOnly(libs.javax.annotation.api)
    compileOnly(libs.lombok)
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.plugin.annotations)
    compileOnly(libs.maven.core)

    annotationProcessor(libs.lombok)

    runtimeOnly(libs.aspectj.weaver)

    mavenPluginDescriptorDependencies(libs.slf4j.api)
    mavenPluginDescriptorDependencies(libs.aspectj.rt)
    mavenPluginDescriptorDependencies(libs.javaparser.symbol.solver.core)
    mavenPluginDescriptorDependencies(libs.h2)
    mavenPluginDescriptorDependencies(libs.aspectj.weaver)
    mavenPluginDescriptorDependencies(libs.grpc.netty.shaded)
    mavenPluginDescriptorDependencies(libs.grpc.protobuf)
    mavenPluginDescriptorDependencies(libs.grpc.stub)
    mavenPluginDescriptorDependencies(libs.protobuf.java)

    testImplementation(platform(libs.junit.bom))
    testImplementation(platform(libs.mockito.bom))
    testImplementation(libs.byteman)
    testImplementation(libs.aspectj.weaver)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.archunit.junit)
    testImplementation(libs.byte.buddy.agent)
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.plugin.annotations)
    testImplementation(libs.maven.core)
    testImplementation(gradleTestKit())

    testRuntimeOnly(libs.aspectj.weaver)
    testRuntimeOnly(libs.junit.jupiter.engine)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

mavenPlugin {
    goalPrefix.set("forensics")
    dependencies.set(mavenPluginDescriptorDependencies)
}

configurations.all {
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
            }
        }
    }
}

val java17 = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:all"))
}

tasks.withType<Javadoc>().configureEach {
    enabled = false
}

abstract class AspectJWeaverAgentArgumentProvider : CommandLineArgumentProvider {
    @get:Classpath
    abstract val agentClasspath: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> {
        val weaverJar = agentClasspath.files.firstOrNull { it.name.startsWith("aspectjweaver") }
            ?: throw GradleException("aspectjweaver*.jar not found. Add 'aspectjAgent(libs.aspectj.weaver)'.")

        val path = weaverJar.absolutePath
        return listOf("-javaagent:${if (path.any { it.isWhitespace() }) "\"$path\"" else path}")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    javaLauncher.set(java17)
    jvmArgumentProviders.add(
        objects.newInstance<AspectJWeaverAgentArgumentProvider>().apply {
            agentClasspath.from(aspectjAgent)
        }
    )
    jvmArgs("-Xshare:off")
    finalizedBy(tasks.jacocoTestReport)
    maxParallelForks = 1
    systemProperty("org.aspectj.weaver.showWeaveInfo", "false")
    systemProperty("aj.weaving.verbose", "false")
    systemProperty("org.aspectj.weaver.loadtime.configuration", "classpath:META-INF/aop.xml")
    systemProperty("forensics.aspect.enabled", "true")
    testLogging {
        events("FAILED", "SKIPPED")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "forensics-tracing",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "de.burger.forensics.tracing"
        )
    }
}

gradlePlugin {
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
                ?: "de.burger.forensics.btmgen"
            implementationClass = providers.gradleProperty("PLUGIN_IMPL_CLASS").orNull
                ?: "de.burger.forensics.plugin.btmgen.gradle.BtmGenPlugin"
            displayName = "Forensics Tracing Gradle Plugin"
            description = providers.gradleProperty("POM_DESCRIPTION").orNull
                ?: "Submits Gradle build analysis requests to the Forensics Analytics server over gRPC."
            tags.set(listOf("forensics", "tracing", "grpc", "java"))
        }
    }
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

        val generatedPackages = setOf(
            "de.burger.forensics.analytics.ingestion.v1"
        )
        val packages = buildList {
            for (index in 0 until packageNodes.length) {
                val node = packageNodes.item(index)
                val counters = (0 until node.childNodes.length)
                    .map { node.childNodes.item(it) }
                    .filter { it.nodeName == "counter" }
                val attrs = node.attributes
                val name = attrs.getNamedItem("name")?.nodeValue ?: "unknown"
                val packageName = name.replace('/', '.')
                if (packageName in generatedPackages) {
                    continue
                }
                val lineCounter = counters.firstOrNull { it.attributes.getNamedItem("type")?.nodeValue == "LINE" }
                val branchCounter = counters.firstOrNull { it.attributes.getNamedItem("type")?.nodeValue == "BRANCH" }
                val missedLines = lineCounter?.attributes?.getNamedItem("missed")?.nodeValue?.toIntOrNull() ?: 0
                val coveredLines = lineCounter?.attributes?.getNamedItem("covered")?.nodeValue?.toIntOrNull() ?: 0
                val missedBranches = branchCounter?.attributes?.getNamedItem("missed")?.nodeValue?.toIntOrNull() ?: 0
                val coveredBranches = branchCounter?.attributes?.getNamedItem("covered")?.nodeValue?.toIntOrNull() ?: 0
                add(PackageCoverage(packageName, missedLines, coveredLines, missedBranches, coveredBranches, branchCounter != null))
            }
        }.sortedWith(compareByDescending<PackageCoverage> { it.impact }.thenBy { it.name })

        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        val lineThresholdValue = lineThreshold.get()
        val branchThresholdValue = branchThreshold.get()
        val failures = packages.filter { pkg ->
            pkg.lineCoverage < lineThresholdValue || (pkg.branchCoverage?.let { it < branchThresholdValue } ?: false)
        }

        report.bufferedWriter().use { writer ->
            writer.appendLine("Package coverage report")
            writer.appendLine("Line threshold: ${(lineThresholdValue * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)}%")
            writer.appendLine("Branch threshold: ${(branchThresholdValue * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)}%")
            writer.appendLine("packageName\tlineCoverage\tbranchCoverage\tmissedLines\tmissedBranches\ttotalLines\ttotalBranches")
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
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Generates a per-package coverage report and fails below the configured thresholds."
    dependsOn(tasks.jacocoTestReport)
    jacocoXml.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    reportFile.set(layout.buildDirectory.file("reports/coverage/package-coverage.txt"))
    lineThreshold.set(BigDecimal("0.80"))
    branchThreshold.set(BigDecimal("0.80"))
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
        }
    }
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
        tasks.named("sonar").configure { enabled = false }
    }
    properties {
        property("sonar.projectKey", "MatthiasBurger-Coder_forensics_tracing")
        property("sonar.organization", "matthiasburger-coder")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.absolutePath
        )
    }
}
