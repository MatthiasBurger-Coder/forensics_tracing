// DEST: src/test/java/de/burger/forensics/plugin/GenerateBtmTaskFunctionalTest.java
package de.burger.forensics.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class GenerateBtmTaskFunctionalTest {

    @Test
    void generatesTracingRulesForSampleKotlinSources() throws IOException {
        File projectDir = Files.createTempDirectory("btmgen-functional-test").toFile();
        projectDir.deleteOnExit();
        writeSettings(projectDir);
        writeBuildScript(projectDir);
        writeSampleSource(projectDir);

        var result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments("generateBtmRules", "--stacktrace")
                .withPluginClasspath()
                .build();

        BuildTask task = result.task(":generateBtmRules");
        assertEquals(TaskOutcome.SUCCESS, task == null ? null : task.getOutcome(),
                "generateBtmRules should succeed");

        File outputDir = new File(projectDir, "build/forensics");
        File[] files = outputDir.listFiles((dir, name) -> name.startsWith("tracing-") && name.endsWith(".btm"));
        assertFalse(files == null || files.length == 0, "Byteman output should be generated");

        List<File> outputFiles = Arrays.stream(files)
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());
        String output = outputFiles.stream()
                .map(file -> {
                    try {
                        return Files.readString(file.toPath());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.joining("\n"));
        System.out.println("[DEBUG_LOG] Output:\n" + output);

        assertTrue(output.contains("enter@de.burger.forensics.sample.SampleFlowKt.decisionFlow"));
        assertTrue(output.contains("METHOD decisionFlow(..)"),
                "METHOD should include (..) to indicate any parameters");
        assertTrue(output.contains("if-true"));
        assertTrue(output.contains(":case"));
        assertTrue(output.contains("write-statusFlag"));
        assertTrue(output.contains("when { … }"),
                "subject-less when should emit a selector placeholder");

        File logFile = new File(projectDir, "logs/forensics-btmgen.log");
        assertTrue(logFile.exists() && logFile.isFile(),
                "Expected logfile to be created at logs/forensics-btmgen.log");
    }

    private void writeSettings(File projectDir) throws IOException {
        Files.writeString(new File(projectDir, "settings.gradle.kts").toPath(),
                "rootProject.name = \"sample-project\"\n");
    }

    private void writeBuildScript(File projectDir) throws IOException {
        String script = String.join("\n",
                "plugins {",
                "    id(\"de.burger.forensics.btmgen\")",
                "}",
                "",
                "repositories {",
                "    mavenCentral()",
                "}",
                "",
                "forensicsBtmGen {",
                "    // keep for backward compatibility, but task is configured explicitly below",
                "    pkgPrefix.set(\"de.burger.forensics.sample\")",
                "    trackedVars.set(listOf(\"statusFlag\"))",
                "}",
                "",
                "// The plugin no longer registers tasks automatically; register it explicitly",
                "tasks.register<de.burger.forensics.plugin.GenerateBtmTask>(\"generateBtmRules\") {",
                "    // Configure explicitly to avoid depending on extension wiring in tests",
                "    srcDirs.set(listOf(\"src/main/kotlin\"))",
                "    packagePrefix.set(\"de.burger.forensics.sample\")",
                "    helperFqn.set(\"de.burger.forensics.ForensicsHelper\")",
                "    entryExit.set(true)",
                "    trackedVars.set(listOf(\"statusFlag\"))",
                "    includeJava.set(false)",
                "    includeTimestamp.set(false)",
                "    maxStringLength.set(0)",
                "    pkgPrefixes.set(emptyList())",
                "    includePatterns.set(emptyList())",
                "    excludePatterns.set(emptyList())",
                "    parallelism.set(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))",
                "    shards.set(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))",
                "    gzipOutput.set(false)",
                "    filePrefix.set(\"tracing-\")",
                "    rotateMaxBytesPerFile.set(4L * 1024 * 1024)",
                "    rotateIntervalSeconds.set(0)",
                "    flushThresholdBytes.set(64 * 1024)",
                "    flushIntervalMillis.set(2000)",
                "    writerThreadSafe.set(false)",
                "    minBranchesPerMethod.set(0)",
                "    safeMode.set(false)",
                "    forceHelperForWhitelist.set(false)",
                "    maxFileBytes.set(2_000_000)",
                "    useAstScanner.set(true)",
                "    outputDir.set(layout.buildDirectory.dir(\"forensics\"))",
                "}",
                "");
        Files.writeString(new File(projectDir, "build.gradle.kts").toPath(), script);
    }

    private void writeSampleSource(File projectDir) throws IOException {
        File sourceDir = new File(projectDir, "src/main/kotlin/de/burger/forensics/sample");
        if (!sourceDir.mkdirs() && !sourceDir.exists()) {
            throw new IOException("Failed to create sample source directory");
        }
        String source = String.join("\n",
                "package de.burger.forensics.sample",
                "",
                "@Suppress(\"UNUSED_PARAMETER\")",
                "fun decisionFlow(customerType: String, amount: Int, subject: Any?): Boolean {",
                "    var statusFlag = false",
                "    if (customerType == \"VIP\") {",
                "        statusFlag = true",
                "    } else if (amount > 10_000) {",
                "        statusFlag = true",
                "    } else {",
                "        statusFlag = amount > 0",
                "    }",
                "",
                "    val normalized = customerType.trim().uppercase()",
                "    when (normalized) {",
                "        \"VIP\" -> statusFlag = true",
                "        \"BLOCKED\", \"FRAUD\" -> statusFlag = false",
                "        else -> statusFlag = amount > 100 && (subject is String)",
                "    }",
                "",
                "    // subject-less when",
                "    when {",
                "        amount > 5000 -> statusFlag = true",
                "        else -> {",
                "            // no-op",
                "        }",
                "    }",
                "",
                "    if (subject is Number) {",
                "        statusFlag = subject.toDouble() > 0.0",
                "    } else if (subject !is String) {",
                "        statusFlag = false",
                "    }",
                "",
                "    return statusFlag",
                "}",
                "",
                "fun renderDecision(statusFlag: Boolean): String = when (statusFlag) {",
                "    true -> \"APPROVED\"",
                "    false -> \"DECLINED\"",
                "}");
        Files.writeString(new File(sourceDir, "SampleFlow.kt").toPath(), source);
    }

    @Test
    void subjectlessWhenPlaceholderIsDistinctive() {
        String placeholder = "when { … }";
        assertTrue(placeholder.contains("…"), "placeholder should include a Unicode ellipsis");
        assertTrue(placeholder.contains(" "));
        assertTrue(placeholder.contains("{") && placeholder.contains("}"));
        assertFalse(placeholder.matches("^[A-Za-z_][A-Za-z0-9_]*$"),
                "placeholder must not be a valid simple identifier");
    }
}
