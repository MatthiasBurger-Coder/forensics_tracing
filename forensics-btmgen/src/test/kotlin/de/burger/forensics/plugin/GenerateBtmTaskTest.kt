package de.burger.forensics.plugin

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateBtmTaskTest {

    @Test
    fun `java rules remain stable with parallelism`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateBtmTest", GenerateBtmTask::class.java).get()

        val sourceDir = Files.createTempDirectory("btmgen-java-src").toFile()
        val slowSource = buildString {
            appendLine("package com.example;")
            appendLine("public class Alpha {")
            appendLine("    public void slow(int value) {")
            repeat(2_000) { idx ->
                appendLine("        if (value == $idx) { System.out.println($idx); }")
            }
            appendLine("    }")
            appendLine("}")
        }
        File(sourceDir, "Alpha.java").writeText(slowSource)

        val fastSource = """
            package com.example;

            public class Beta {
                public void fast(int value) {
                    if (value == 0) {
                        System.out.println(value);
                    }
                }
            }
        """.trimIndent()
        File(sourceDir, "Beta.java").writeText(fastSource)

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
        task.parallelism.set(4)
        task.shardOutput.set(1)
        task.gzipOutput.set(false)
        task.minBranchesPerMethod.set(0)

        val outputDir = Files.createTempDirectory("btm-task-output")
        task.outputDir.set(project.layout.dir(project.provider { outputDir.toFile() }))

        task.generate()
        val outputFile = outputDir.resolve("tracing.btm").toFile()
        val firstRun = outputFile.readText()

        task.generate()
        val secondRun = outputFile.readText()

        assertEquals(firstRun, secondRun, "Parallel generation should be deterministic")

        val alphaRuleIndex = firstRun.indexOf("RULE enter@com.example.Alpha.slow")
        val betaRuleIndex = firstRun.indexOf("RULE enter@com.example.Beta.fast")
        assertTrue(alphaRuleIndex >= 0 && betaRuleIndex >= 0, "Expected rules for both classes to be present")
        assertTrue(alphaRuleIndex < betaRuleIndex, "Alpha rules should precede Beta rules in deterministic output\n$firstRun")
    }
}

