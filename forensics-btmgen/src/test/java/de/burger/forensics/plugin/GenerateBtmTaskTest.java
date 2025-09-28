// DEST: src/test/java/de/burger/forensics/plugin/GenerateBtmTaskTest.java
package de.burger.forensics.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class GenerateBtmTaskTest {

    @Test
    void javaRulesRemainStableWithParallelism() throws IOException {
        var project = ProjectBuilder.builder().build();
        GenerateBtmTask task = project.getTasks().register("generateBtmTest", GenerateBtmTask.class).get();

        File sourceDir = Files.createTempDirectory("btmgen-java-src").toFile();
        StringBuilder slowSource = new StringBuilder();
        slowSource.append("package com.example;\n");
        slowSource.append("public class Alpha {\n");
        slowSource.append("    public void slow(int value) {\n");
        for (int idx = 0; idx < 2000; idx++) {
            slowSource.append("        if (value == ").append(idx)
                    .append(") { System.out.println(").append(idx).append("); }\n");
        }
        slowSource.append("    }\n");
        slowSource.append("}\n");
        Files.writeString(new File(sourceDir, "Alpha.java").toPath(), slowSource.toString());

        String fastSource = String.join("\n",
                "package com.example;",
                "",
                "public class Beta {",
                "    public void fast(int value) {",
                "        if (value == 0) {",
                "            System.out.println(value);",
                "        }",
                "    }",
                "}");
        Files.writeString(new File(sourceDir, "Beta.java").toPath(), fastSource);

        task.getSrcDirs().set(Collections.singletonList(sourceDir.getAbsolutePath()));
        task.getPackagePrefix().set("com.example");
        task.getHelperFqn().set("helper.Helper");
        task.getEntryExit().set(true);
        task.getTrackedVars().set(Collections.emptyList());
        task.getIncludeJava().set(true);
        task.getUseAstScanner().set(true);
        task.getIncludeTimestamp().set(false);
        task.getMaxStringLength().set(200);
        task.getPkgPrefixes().set(Collections.emptyList());
        task.getIncludePatterns().set(Collections.emptyList());
        task.getExcludePatterns().set(Collections.emptyList());
        task.getParallelism().set(4);
        task.getShards().set(1);
        task.getGzipOutput().set(false);
        task.getMinBranchesPerMethod().set(0);

        Path outputDir = Files.createTempDirectory("btm-task-output");
        task.getOutputDir().set(project.getLayout().dir(project.provider(() -> outputDir.toFile())));

        task.generate();
        File outputFile = outputDir.resolve("tracing-0001-00001.btm").toFile();
        String firstRun = Files.readString(outputFile.toPath());

        task.generate();
        String secondRun = Files.readString(outputFile.toPath());

        assertEquals(firstRun, secondRun, "Parallel generation should be deterministic");

        int alphaRuleIndex = firstRun.indexOf("RULE enter@com.example.Alpha.slow");
        int betaRuleIndex = firstRun.indexOf("RULE enter@com.example.Beta.fast");
        assertTrue(alphaRuleIndex >= 0 && betaRuleIndex >= 0,
                "Expected rules for both classes to be present");
        assertTrue(alphaRuleIndex < betaRuleIndex,
                "Alpha rules should precede Beta rules in deterministic output\n" + firstRun);
    }

    @Test
    void javaEntryExitRulesEmittedOnceWithAstScanner() throws IOException {
        var project = ProjectBuilder.builder().build();
        GenerateBtmTask task = project.getTasks().register("generateBtmOnce", GenerateBtmTask.class).get();

        File sourceDir = Files.createTempDirectory("btmgen-java-ast").toFile();
        String javaSource = String.join("\n",
                "package com.example;",
                "",
                "public class Single {",
                "    public void demo(int value) {",
                "        if (value > 0) {",
                "            System.out.println(value);",
                "        }",
                "    }",
                "}");
        Files.writeString(new File(sourceDir, "Single.java").toPath(), javaSource);

        task.getSrcDirs().set(Collections.singletonList(sourceDir.getAbsolutePath()));
        task.getPackagePrefix().set("com.example");
        task.getHelperFqn().set("helper.Helper");
        task.getEntryExit().set(true);
        task.getTrackedVars().set(Collections.emptyList());
        task.getIncludeJava().set(true);
        task.getIncludeTimestamp().set(false);
        task.getMaxStringLength().set(200);
        task.getPkgPrefixes().set(Collections.emptyList());
        task.getIncludePatterns().set(Collections.emptyList());
        task.getExcludePatterns().set(Collections.emptyList());
        task.getParallelism().set(1);
        task.getShards().set(1);
        task.getGzipOutput().set(false);
        task.getMinBranchesPerMethod().set(0);

        Path outputDir = Files.createTempDirectory("btm-task-output-single");
        task.getOutputDir().set(project.getLayout().dir(project.provider(() -> outputDir.toFile())));

        task.generate();

        File outputFile = outputDir.resolve("tracing-0001-00001.btm").toFile();
        String content = Files.readString(outputFile.toPath());
        Pattern enterPattern = Pattern.compile("RULE\\s+enter@com\\.example\\.Single\\.demo\\b");
        Pattern exitPattern = Pattern.compile("RULE\\s+exit@com\\.example\\.Single\\.demo\\b");
        assertEquals(1, countMatches(enterPattern.matcher(content)),
                "Entry rule should be emitted exactly once");
        assertEquals(1, countMatches(exitPattern.matcher(content)),
                "Exit rule should be emitted exactly once");
    }

    private int countMatches(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
