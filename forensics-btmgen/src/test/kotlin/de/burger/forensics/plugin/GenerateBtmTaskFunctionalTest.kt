package de.burger.forensics.plugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class GenerateBtmTaskFunctionalTest {

    @Test
    fun generatesTracingRulesForSampleKotlinSources() {
        val projectDir = Files.createTempDirectory("btmgen-functional-test").toFile().apply { deleteOnExit() }
        writeSettings(projectDir)
        writeBuildScript(projectDir)
        writeSampleSource(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateBtmRules", "--stacktrace")
            .withPluginClasspath()
            .build()

        val task = result.task(":generateBtmRules")
        assertEquals(TaskOutcome.SUCCESS, task?.outcome, "generateBtmRules should succeed")

        val outputDir = File(projectDir, "build/forensics")
        val outputFiles = outputDir.listFiles { _, name ->
            name.startsWith("tracing-") && name.endsWith(".btm")
        }?.sortedBy { it.name }
        assertTrue(!outputFiles.isNullOrEmpty(), "Byteman output should be generated")

        val output = outputFiles!!.joinToString("\n") { it.readText() }
        assertTrue(output.contains("enter@de.burger.forensics.sample.SampleFlowKt.decisionFlow"))
        assertTrue(output.contains("METHOD decisionFlow(..)"), "METHOD should include (..) to indicate any parameters")
        assertTrue(output.contains("if-true"))
        assertTrue(output.contains(":case"))
        assertTrue(output.contains("write-statusFlag"))
        // ensure subject-less when logs a selector-like event
        assertTrue(output.contains("when { … }"), "subject-less when should emit a selector placeholder")
    }

    private fun writeSettings(projectDir: File) {
        File(projectDir, "settings.gradle.kts").writeText(
            "rootProject.name = \"sample-project\"\n"
        )
    }

    private fun writeBuildScript(projectDir: File) {
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("de.burger.forensics.btmgen")
            }

            repositories {
                mavenCentral()
            }

            forensicsBtmGen {
                // keep for backward compatibility, but task is configured explicitly below
                pkgPrefix.set("de.burger.forensics.sample")
                trackedVars.set(listOf("statusFlag"))
            }

            // The plugin no longer registers tasks automatically; register it explicitly
            tasks.register<de.burger.forensics.plugin.GenerateBtmTask>("generateBtmRules") {
                // Configure explicitly to avoid depending on extension wiring in tests
                srcDirs.set(listOf("src/main/kotlin"))
                packagePrefix.set("de.burger.forensics.sample")
                helperFqn.set("de.burger.forensics.ForensicsHelper")
                entryExit.set(true)
                trackedVars.set(listOf("statusFlag"))
                includeJava.set(false)
                includeTimestamp.set(false)
                maxStringLength.set(0)
                pkgPrefixes.set(emptyList())
                includePatterns.set(emptyList())
                excludePatterns.set(emptyList())
                parallelism.set(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
                shards.set(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
                gzipOutput.set(false)
                filePrefix.set("tracing-")
                rotateMaxBytesPerFile.set(4L * 1024 * 1024)
                rotateIntervalSeconds.set(0)
                flushThresholdBytes.set(64 * 1024)
                flushIntervalMillis.set(2000)
                writerThreadSafe.set(false)
                minBranchesPerMethod.set(0)
                safeMode.set(false)
                forceHelperForWhitelist.set(false)
                maxFileBytes.set(2_000_000)
                useAstScanner.set(true)
                outputDir.set(layout.buildDirectory.dir("forensics"))
            }
            """.trimIndent()
        )
    }

    private fun writeSampleSource(projectDir: File) {
        val sourceDir = File(projectDir, "src/main/kotlin/de/burger/forensics/sample")
        sourceDir.mkdirs()
        File(sourceDir, "SampleFlow.kt").writeText(
            """
            package de.burger.forensics.sample

            @Suppress("UNUSED_PARAMETER")
            fun decisionFlow(customerType: String, amount: Int, subject: Any?): Boolean {
                var statusFlag = false
                if (customerType == "VIP") {
                    statusFlag = true
                } else if (amount > 10_000) {
                    statusFlag = true
                } else {
                    statusFlag = amount > 0
                }

                val normalized = customerType.trim().uppercase()
                when (normalized) {
                    "VIP" -> statusFlag = true
                    "BLOCKED", "FRAUD" -> statusFlag = false
                    else -> statusFlag = amount > 100 && (subject is String)
                }

                // subject-less when
                when {
                    amount > 5000 -> statusFlag = true
                    else -> {
                        // no-op
                    }
                }

                if (subject is Number) {
                    statusFlag = subject.toDouble() > 0.0
                } else if (subject !is String) {
                    statusFlag = false
                }

                return statusFlag
            }

            fun renderDecision(statusFlag: Boolean): String = when (statusFlag) {
                true -> "APPROVED"
                false -> "DECLINED"
            }
            """.trimIndent()
        )
    }
    @Test
    fun subjectlessWhenPlaceholderIsDistinctive() {
        val placeholder = "when { … }"
        // Contains Unicode ellipsis ensuring it cannot be typed accidentally and is visually distinctive
        assertTrue(placeholder.contains('…'), "placeholder should include a Unicode ellipsis")
        // Contains spaces and braces so it cannot be a plain Java/Kotlin identifier
        assertTrue(placeholder.contains(' '))
        assertTrue(placeholder.contains('{') && placeholder.contains('}'))
        // Not a valid plain identifier (Java/Kotlin simple name)
        val simpleIdentifier = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        assertTrue(!simpleIdentifier.matches(placeholder), "placeholder must not be a valid simple identifier")
    }
}
