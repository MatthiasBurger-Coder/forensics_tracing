package de.burger.forensics.plugin

import java.io.File
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class RuleParitySmokeTest {

    @Test
    fun `ast scanner matches legacy rule volume`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("parity", GenerateBtmTask::class.java).get()

        val sourceDir = Files.createTempDirectory("parity-src").toFile()
        File(sourceDir, "Mix.kt").writeText(
            """
            package com.example

            class Mix {
                fun choice(flag: Int): String {
                    return when (flag) {
                        1 -> "one"
                        else -> "other"
                    }
                }
            }
            """.trimIndent()
        )
        File(sourceDir, "Mix.java").writeText(
            """
            package com.example;

            public class MixJava {
                public void tap(boolean ready) {
                    if (ready) {
                        System.out.println("r");
                    }
                }
            }
            """.trimIndent()
        )

        task.srcDirs.set(listOf(sourceDir.absolutePath))
        task.packagePrefix.set("com.example")
        task.helperFqn.set("helper.Helper")
        task.entryExit.set(true)
        task.trackedVars.set(emptyList())
        task.includeJava.set(true)
        task.includeTimestamp.set(false)
        task.maxStringLength.set(200)
        task.pkgPrefixes.set(emptyList())
        task.includePatterns.set(emptyList())
        task.excludePatterns.set(emptyList())
        task.parallelism.set(1)
        task.shards.set(1)
        task.gzipOutput.set(false)
        task.minBranchesPerMethod.set(0)

        val outputDir = Files.createTempDirectory("parity-out")
        task.outputDir.set(project.layout.dir(project.provider { outputDir.toFile() }))

        task.useAstScanner.set(true)
        task.generate()
        val astContent = outputDir.resolve("tracing-0001-00001.btm").toFile().readText()
        val astRules = astContent.lines().count { it.startsWith("RULE ") }

        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()

        task.useAstScanner.set(false)
        task.generate()
        val legacyContent = outputDir.resolve("tracing-0001-00001.btm").toFile().readText()
        val legacyRules = legacyContent.lines().count { it.startsWith("RULE ") }

        assertThat(astRules).isGreaterThan(0)
        assertThat(legacyRules).isGreaterThan(0)
        assertThat(kotlin.math.abs(astRules - legacyRules)).isLessThanOrEqualTo(5)
    }
}
