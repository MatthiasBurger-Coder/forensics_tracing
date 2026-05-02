import de.burger.forensics.plugin.btmgen.gradle.GenerateBtmTask
import org.gradle.api.GradleException
import java.io.File

plugins {
    id("de.burger.forensics.btmgen")
}

val excludedDirectoryNames = setOf(
    ".git",
    ".gradle",
    ".idea",
    ".mvn",
    ".forensics",
    "target"
)

val includeTestsuite = booleanGradleProperty("forensics.wildfly.includeTestsuite", false)
val includeTestSources = booleanGradleProperty("forensics.wildfly.includeTestSources", false)
val minBranches = intGradleProperty("forensics.wildfly.minBranches", 0)
val packageIncludes = stringGradleProperty("forensics.wildfly.includes") ?: ""

val forensicsPluginRoot = providers.gradleProperty("forensicsPluginRoot")
    .map { file(it).canonicalFile }
    .getOrElse(layout.projectDirectory.dir("../..").asFile.canonicalFile)

val wildFlyRoot = providers.gradleProperty("wildflyRoot")
    .map { file(it).canonicalFile }
    .getOrElse(forensicsPluginRoot.resolve("../wildfly").canonicalFile)

validateWildFlyRoot(wildFlyRoot)

val wildFlySourceRoots = discoverMavenStyleSourceRoots(
    root = wildFlyRoot,
    includeTestsuite = includeTestsuite,
    includeTestSources = includeTestSources
)

val configuredBtmOutputFile = layout.projectDirectory.file(".forensics/build/btm/wildfly-main.btm").asFile
val configuredProfileReportFile = layout.projectDirectory.file(".forensics/build/reports/wildfly-scan-profile.json").asFile
val configuredCacheDatabaseFile = layout.projectDirectory.file(".forensics/cache/wildfly-scan-cache").asFile

btmGen {
    scanSubprojects.set(false)
    sourceRoot.set(layout.projectDirectory.dir(".forensics/sidecar-source-root").asFile)
    sourceRoots.from(wildFlySourceRoots)
    outputFile.set(configuredBtmOutputFile)
    includes.set(packageIncludes)
    minBranchesPerMethod.set(minBranches)
    cacheEnabled.set(true)
    cacheBackend.set("h2")
    cacheDatabaseFile.set(configuredCacheDatabaseFile)
    profilingEnabled.set(true)
    profileReportFile.set(configuredProfileReportFile)
    strictParsing.set(false)
    dependencyAwareInvalidation.set(false)
}

tasks.named<GenerateBtmTask>("generateBtmRules") {
    includeEntryExit.set(booleanGradleProperty("forensics.wildfly.includeEntryExit", true))
}

tasks.register("printWildFlyForensicsSourceRoots") {
    group = "forensics"
    description = "Prints the WildFly source roots selected for the Forensics BTM generator."

    doLast {
        println("Selected WildFly root: ${wildFlyRoot.absolutePath}")
        println("Number of discovered source roots: ${wildFlySourceRoots.size}")
        wildFlySourceRoots.forEach { sourceRoot ->
            println(sourceRoot.absolutePath)
        }
        println("Testsuite included: $includeTestsuite")
        println("Test sources included: $includeTestSources")
        println("Configured BTM output file: ${configuredBtmOutputFile.absolutePath}")
        println("Configured profiling report file: ${configuredProfileReportFile.absolutePath}")
        println("Configured cache location: ${configuredCacheDatabaseFile.absolutePath}")
    }
}

fun stringGradleProperty(name: String): String? =
    providers.gradleProperty(name)
        .orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

fun booleanGradleProperty(name: String, defaultValue: Boolean): Boolean =
    when (val value = stringGradleProperty(name)?.lowercase()) {
        null -> defaultValue
        "true" -> true
        "false" -> false
        else -> throw GradleException("Property '$name' must be either true or false, but was '$value'.")
    }

fun intGradleProperty(name: String, defaultValue: Int): Int {
    val value = stringGradleProperty(name) ?: return defaultValue
    return value.toIntOrNull()
        ?: throw GradleException("Property '$name' must be an integer, but was '$value'.")
}

fun validateWildFlyRoot(root: File) {
    val missingRequiredFiles = listOf("pom.xml", "mvnw")
        .filterNot { requiredFile -> root.resolve(requiredFile).isFile }

    if (!root.isDirectory || missingRequiredFiles.isNotEmpty()) {
        throw GradleException(
            "Selected directory '${root.absolutePath}' is not a WildFly checkout or Maven-style WildFly root. " +
                "Expected a directory containing pom.xml and mvnw. Missing: ${missingRequiredFiles.joinToString(", ")}"
        )
    }
}

fun discoverMavenStyleSourceRoots(
    root: File,
    includeTestsuite: Boolean,
    includeTestSources: Boolean
): List<File> {
    val selectedSuffixes = buildList {
        add("/src/main/java")
        if (includeTestSources) {
            add("/src/test/java")
        }
    }

    return root.walkTopDown()
        .onEnter { directory ->
            shouldEnterDirectory(root, directory, includeTestsuite)
        }
        .filter { candidate ->
            candidate.isDirectory && selectedSuffixes.any { suffix ->
                candidate.invariantSeparatorsPath.endsWith(suffix)
            }
        }
        .sortedBy { candidate -> candidate.absolutePath }
        .toList()
}

fun shouldEnterDirectory(root: File, directory: File, includeTestsuite: Boolean): Boolean {
    if (directory == root) {
        return true
    }
    if (directory.name in excludedDirectoryNames) {
        return false
    }
    return includeTestsuite || !directory.relativeTo(root)
        .invariantSeparatorsPath
        .split("/")
        .contains("testsuite")
}
