// DEST: src/test/java/de/burger/forensics/plugin/SafeModeRegistrationTest.java
package de.burger.forensics.plugin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class SafeModeRegistrationTest {

    @Test
    void unsafeExpressionRegistersEvaluatorWithTranslation() throws IOException {
        var project = ProjectBuilder.builder().build();
        GenerateBtmTask task = project.getTasks().register("generateUnsafe", GenerateBtmTask.class).get();
        File sourceDir = Files.createTempDirectory("btmgen-unsafe-src").toFile();
        writeSource(sourceDir, "Demo.kt", String.join("\n",
                "package com.example",
                "",
                "class Demo {",
                "    fun sample(value: String?) {",
                "        if (value != null) {",
                "            println(value);",
                "        }",
                "    }",
                "}")
        );
        configureTask(task, sourceDir);

        Path outputDir = Files.createTempDirectory("btm-output-unsafe");
        task.getOutputDir().set(project.getLayout().dir(project.provider(() -> outputDir.toFile())));

        task.generate();

        String content = Files.readString(outputDir.resolve("tracing-0001-00001.btm"));
        Pattern pattern = Pattern.compile("IF \\((org\\.example\\.trace\\.SafeEval\\.ifMatch\\(\"([^\"]+)\"\\))\\)");
        Matcher matcher = pattern.matcher(content);
        assertTrue(matcher.find(), "Expected IF guard to register evaluator");
        String ruleId = matcher.group(2);
        assertNotNull(ruleId, "Rule identifier should be captured");
        assertTrue(content.contains("DO org.example.trace.SafeEval.register(\"" + ruleId
                + "\", new org.example.trace.SafeEval.Evaluator() {"));
        assertTrue(content.contains("return !org.example.trace.SafeEval.ifEq(value, null);"));
    }

    @Test
    void unsupportedExpressionFallsBackToTrueInEvaluatorBody() throws IOException {
        var project = ProjectBuilder.builder().build();
        GenerateBtmTask task = project.getTasks().register("generateFallback", GenerateBtmTask.class).get();
        File sourceDir = Files.createTempDirectory("btmgen-fallback-src").toFile();
        writeSource(sourceDir, "Demo.kt", String.join("\n",
                "package com.example",
                "",
                "class Demo {",
                "    fun sample(value: String?) {",
                "        if (value != null && value.equals(\"OK\")) {",
                "            println(value);",
                "        }",
                "    }",
                "}")
        );
        configureTask(task, sourceDir);

        Path outputDir = Files.createTempDirectory("btm-output-fallback");
        task.getOutputDir().set(project.getLayout().dir(project.provider(() -> outputDir.toFile())));

        task.generate();

        String content = Files.readString(outputDir.resolve("tracing-0001-00001.btm"));
        Pattern pattern = Pattern.compile(
                "DO org\\.example\\.trace\\.SafeEval\\.register\\(\"([^\"]+)\", new org\\.example\\.trace\\.SafeEval\\.Evaluator\\(\\) \\{");
        Matcher matcher = pattern.matcher(content);
        assertTrue(matcher.find(), "Expected evaluator registration entry");
        assertTrue(content.contains("return true;"));
    }

    private void configureTask(GenerateBtmTask task, File sourceDir) {
        task.getSrcDirs().set(Collections.singletonList(sourceDir.getAbsolutePath()));
        task.getPackagePrefix().set("com.example");
        task.getHelperFqn().set("org.example.trace.SafeEval");
        task.getEntryExit().set(false);
        task.getTrackedVars().set(Collections.emptyList());
        task.getIncludeJava().set(false);
        task.getIncludeTimestamp().set(false);
        task.getMaxStringLength().set(200);
        task.getPkgPrefixes().set(Collections.emptyList());
        task.getIncludePatterns().set(Collections.emptyList());
        task.getExcludePatterns().set(Collections.emptyList());
        task.getParallelism().set(1);
        task.getShards().set(1);
        task.getGzipOutput().set(false);
        task.getMinBranchesPerMethod().set(0);
        task.getSafeMode().set(true);
        task.getForceHelperForWhitelist().set(false);
    }

    private static void writeSource(File directory, String name, String content) throws IOException {
        Files.writeString(new File(directory, name).toPath(), content);
    }
}
