package de.burger.forensics.plugin

import java.io.File
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class SafeModeRegistrationTest {

    @Test
    fun unsafeExpressionRegistersEvaluatorWithTranslation() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateUnsafe", GenerateBtmTask::class.java).get()
        val sourceDir = Files.createTempDirectory("btmgen-unsafe-src").toFile()
        File(sourceDir, "Demo.kt").writeText(
            """
            package com.example

            class Demo {
                fun sample(value: String?) {
                    if (value != null) {
                        println(value)
                    }
                }
            }
            """.trimIndent()
        )
        configureTask(task, sourceDir)

        val outputDir = Files.createTempDirectory("btm-output-unsafe")
        task.outputDir.set(project.layout.dir(project.provider { outputDir.toFile() }))

        task.generate()

        val content = outputDir.resolve("tracing-0001-00001.btm").toFile().readText()
        val match = Regex("""IF \(org\.example\.trace\.SafeEval\.ifMatch\("([^"]+)"\)\)""").find(content)
        assertThat(match).isNotNull
        val ruleId = match!!.groupValues[1]
        assertThat(content)
            .contains("DO org.example.trace.SafeEval.register(\"$ruleId\", new org.example.trace.SafeEval.Evaluator() {")
            .contains("return !org.example.trace.SafeEval.ifEq(value, null);")
    }

    @Test
    fun unsupportedExpressionFallsBackToTrueInEvaluatorBody() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateFallback", GenerateBtmTask::class.java).get()
        val sourceDir = Files.createTempDirectory("btmgen-fallback-src").toFile()
        File(sourceDir, "Demo.kt").writeText(
            """
            package com.example

            class Demo {
                fun sample(value: String?) {
                    if (value != null && value.equals(\"OK\")) {
                        println(value)
                    }
                }
            }
            """.trimIndent()
        )
        configureTask(task, sourceDir)

        val outputDir = Files.createTempDirectory("btm-output-fallback")
        task.outputDir.set(project.layout.dir(project.provider { outputDir.toFile() }))

        task.generate()

        val content = outputDir.resolve("tracing-0001-00001.btm").toFile().readText()
        val match = Regex("""DO org\.example\.trace\.SafeEval\.register\("([^"]+)", new org\.example\.trace\.SafeEval\.Evaluator\(\) \{""")
            .find(content)
        assertThat(match).isNotNull
        assertThat(content).contains("return true;")
    }

    private fun configureTask(task: GenerateBtmTask, sourceDir: File) {
        task.srcDirs.set(listOf(sourceDir.absolutePath))
        task.packagePrefix.set("com.example")
        task.helperFqn.set("org.example.trace.SafeEval")
        task.entryExit.set(false)
        task.trackedVars.set(emptyList())
        task.includeJava.set(false)
        task.includeTimestamp.set(false)
        task.maxStringLength.set(200)
        task.pkgPrefixes.set(emptyList())
        task.includePatterns.set(emptyList())
        task.excludePatterns.set(emptyList())
        task.parallelism.set(1)
        task.shards.set(1)
        task.gzipOutput.set(false)
        task.minBranchesPerMethod.set(0)
        task.safeMode.set(true)
        task.forceHelperForWhitelist.set(false)
    }
}
