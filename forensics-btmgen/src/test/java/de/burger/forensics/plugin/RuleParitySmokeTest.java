// DEST: src/test/java/de/burger/forensics/plugin/RuleParitySmokeTest.java
package de.burger.forensics.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class RuleParitySmokeTest {

    @Test
    void astScannerMatchesLegacyRuleVolume() throws IOException {
        var project = ProjectBuilder.builder().build();
        GenerateBtmTask task = project.getTasks().register("parity", GenerateBtmTask.class).get();

        File sourceDir = Files.createTempDirectory("parity-src").toFile();
        writeText(sourceDir, "Mix.java", String.join("\n",
                "package com.example;",
                "",
                "public class MixJava {",
                "    public void tap(boolean ready) {",
                "        if (ready) {",
                "            System.out.println(\"r\");",
                "        }",
                "    }",
                "",
                "    public int choice(int flag) {",
                "        switch (flag) {",
                "            case 1: return 1;",
                "            default: return 2;",
                "        }",
                "    }",
                "}")
        );

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

        Path outputDir = Files.createTempDirectory("parity-out");
        task.getOutputDir().set(project.getLayout().dir(project.provider(() -> outputDir.toFile())));

        task.getUseAstScanner().set(true);
        task.generate();
        String astContent = Files.readString(outputDir.resolve("tracing-0001-00001.btm"));
        long astRules = astContent.lines().filter(line -> line.startsWith("RULE ")).count();

        deleteRecursively(outputDir);
        Files.createDirectories(outputDir);

        task.getUseAstScanner().set(false);
        task.generate();
        String legacyContent = Files.readString(outputDir.resolve("tracing-0001-00001.btm"));
        long legacyRules = legacyContent.lines().filter(line -> line.startsWith("RULE ")).count();

        assertTrue(astRules > 0, "AST scanner should produce rules");
        assertTrue(legacyRules > 0, "Legacy scanner should produce rules");
        assertTrue(Math.abs(astRules - legacyRules) <= 5,
                "Rule counts should be within acceptable variance: " + astRules + " vs " + legacyRules);
    }

    private static void writeText(File directory, String name, String content) throws IOException {
        File file = new File(directory, name);
        Files.writeString(file.toPath(), content);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup for temporary test directory
                }
            });
        }
    }
}
