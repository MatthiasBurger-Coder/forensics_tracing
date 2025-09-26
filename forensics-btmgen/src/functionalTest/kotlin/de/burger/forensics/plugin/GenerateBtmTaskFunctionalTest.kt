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

        val outputFile = File(projectDir, "build/forensics/tracing.btm")
        assertTrue(outputFile.exists(), "Byteman output should be generated")

        val output = outputFile.readText()
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
