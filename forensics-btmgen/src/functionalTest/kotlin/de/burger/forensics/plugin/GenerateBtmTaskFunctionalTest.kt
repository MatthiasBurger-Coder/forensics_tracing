package de.burger.forensics.plugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateBtmTaskFunctionalTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `generates tracing rules for sample kotlin sources`() {
        val projectDir = tempDir.toFile()
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

        val outputFile = File(projectDir, "build/forensics/tracing.btm")
        assertTrue(outputFile.exists(), "Byteman output should be generated")

        val output = outputFile.readText()
        assertTrue(output.contains("enter@de.burger.forensics.sample.SampleFlowKt.decisionFlow"))
        assertTrue(output.contains("if-true"))
        assertTrue(output.contains(":case"))
        assertTrue(output.contains("write-statusFlag"))
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
                pkgPrefix.set("de.burger.forensics.sample")
                trackedVars.set(listOf("statusFlag"))
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
}
