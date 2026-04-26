package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BtmGenPluginFunctionalTest {

    @Test
    void generateBtmRulesWorksInASimpleJavaProject(@TempDir Path tempDir) throws IOException {
        writeSimpleSettings(tempDir);
        Files.writeString(tempDir.resolve("build.gradle.kts"), simpleBuildScript());
        writeJavaSource(
            tempDir.resolve("src/main/java"),
            "com.example.simple",
            "SimpleService",
            "simpleMethod"
        );

        BuildResult result = runGradle(tempDir, ":generateBtmRules");

        assertEquals(TaskOutcome.SUCCESS, taskOutcome(result, ":generateBtmRules"));
        String output = Files.readString(tempDir.resolve("build/forensics/forensics.btm"));
        assertTrue(output.contains("com.example.simple.SimpleService#simpleMethod"));
    }

    @Test
    void generateBtmRulesReusesTheConfigurationCache(@TempDir Path tempDir) throws IOException {
        writeSimpleSettings(tempDir);
        Files.writeString(tempDir.resolve("build.gradle.kts"), simpleBuildScript());
        writeJavaSource(
            tempDir.resolve("src/main/java"),
            "com.example.cache",
            "CacheService",
            "cacheMethod"
        );

        BuildResult firstRun = runGradle(tempDir, "--configuration-cache", ":generateBtmRules");
        BuildResult secondRun = runGradle(tempDir, "--configuration-cache", ":generateBtmRules");

        assertEquals(TaskOutcome.SUCCESS, taskOutcome(firstRun, ":generateBtmRules"));
        assertTrue(firstRun.getOutput().contains("Configuration cache entry stored."));
        assertEquals(TaskOutcome.UP_TO_DATE, taskOutcome(secondRun, ":generateBtmRules"));
        assertTrue(secondRun.getOutput().contains("Configuration cache entry reused."));
    }

    @Test
    void generateBtmRulesScansJavaSubprojectsFromTheRootProject(@TempDir Path tempDir) throws IOException {
        writeMonorepoSettings(tempDir, "module-a", "module-b");
        Files.writeString(tempDir.resolve("build.gradle.kts"), rootBuildScript());
        Files.writeString(tempDir.resolve("module-a/build.gradle.kts"), defaultJavaSubprojectBuildScript());
        Files.writeString(tempDir.resolve("module-b/build.gradle.kts"), defaultJavaSubprojectBuildScript());

        writeJavaSource(
            tempDir.resolve("module-a/src/main/java"),
            "com.example.modulea",
            "ModuleAService",
            "moduleA"
        );
        writeJavaSource(
            tempDir.resolve("module-b/src/main/java"),
            "com.example.moduleb",
            "ModuleBService",
            "moduleB"
        );

        BuildResult result = runGradle(tempDir, ":generateBtmRules");

        assertEquals(TaskOutcome.SUCCESS, taskOutcome(result, ":generateBtmRules"));
        String output = Files.readString(tempDir.resolve("build/forensics/all-modules.btm"));
        assertTrue(output.contains("com.example.modulea.ModuleAService#moduleA"));
        assertTrue(output.contains("com.example.moduleb.ModuleBService#moduleB"));
        assertFalse(result.getOutput().contains("property 'sourceRoot'"));
        assertFalse(result.getOutput().contains("does not exist"));
    }

    @Test
    void generateBtmRulesScansCustomSubprojectMainSourceSetDirectories(@TempDir Path tempDir) throws IOException {
        writeMonorepoSettings(tempDir, "module-a");
        Files.writeString(tempDir.resolve("build.gradle.kts"), rootBuildScript());
        Files.writeString(tempDir.resolve("module-a/build.gradle.kts"), """
                plugins {
                    `java-library`
                }

                sourceSets {
                    main {
                        java.setSrcDirs(listOf("sources/main/java"))
                    }
                }
                """);

        writeJavaSource(
            tempDir.resolve("module-a/sources/main/java"),
            "com.example.custom",
            "CustomService",
            "customPath"
        );

        BuildResult result = runGradle(tempDir, ":generateBtmRules");

        assertEquals(TaskOutcome.SUCCESS, taskOutcome(result, ":generateBtmRules"));
        String output = Files.readString(tempDir.resolve("build/forensics/all-modules.btm"));
        assertTrue(output.contains("com.example.custom.CustomService#customPath"));
    }

    @Test
    void generateBtmRulesBecomesUpToDateAndRerunsAfterSourceChanges(@TempDir Path tempDir) throws IOException {
        writeMonorepoSettings(tempDir, "module-a");
        Files.writeString(tempDir.resolve("build.gradle.kts"), rootBuildScript());
        Files.writeString(tempDir.resolve("module-a/build.gradle.kts"), defaultJavaSubprojectBuildScript());

        Path javaFile = writeJavaSource(
            tempDir.resolve("module-a/src/main/java"),
            "com.example.modulea",
            "ModuleAService",
            "moduleA"
        );

        BuildResult firstRun = runGradle(tempDir, "--build-cache", ":generateBtmRules");
        BuildResult secondRun = runGradle(tempDir, "--build-cache", ":generateBtmRules");

        assertEquals(TaskOutcome.SUCCESS, taskOutcome(firstRun, ":generateBtmRules"));
        assertEquals(TaskOutcome.UP_TO_DATE, taskOutcome(secondRun, ":generateBtmRules"));

        Files.writeString(javaFile, """
                package com.example.modulea;
                public class ModuleAService {
                  public int moduleA() {
                    if (true) { }
                    switch (1) { case 1 -> {} }
                    return 1;
                  }

                  public int moduleAUpdated() {
                    if (false) { }
                    switch (2) { case 2 -> {} }
                    return 2;
                  }
                }
                """);

        BuildResult thirdRun = runGradle(tempDir, "--build-cache", ":generateBtmRules");

        assertEquals(TaskOutcome.SUCCESS, taskOutcome(thirdRun, ":generateBtmRules"));
        String output = Files.readString(tempDir.resolve("build/forensics/all-modules.btm"));
        assertTrue(output.contains("ModuleAService#moduleAUpdated"));
    }

    @Test
    void generateBtmRulesIgnoresUnrelatedFileChanges(@TempDir Path tempDir) throws IOException {
        writeSimpleSettings(tempDir);
        Files.writeString(tempDir.resolve("build.gradle.kts"), simpleBuildScript());
        writeJavaSource(
            tempDir.resolve("src/main/java"),
            "com.example.unrelated",
            "UnrelatedService",
            "unrelatedMethod"
        );

        BuildResult firstRun = runGradle(tempDir, "--build-cache", ":generateBtmRules");
        Files.writeString(tempDir.resolve("README.md"), "This file must not invalidate Java source scanning.");
        BuildResult secondRun = runGradle(tempDir, "--build-cache", ":generateBtmRules");

        assertEquals(TaskOutcome.SUCCESS, taskOutcome(firstRun, ":generateBtmRules"));
        assertEquals(TaskOutcome.UP_TO_DATE, taskOutcome(secondRun, ":generateBtmRules"));
    }

    private static Path writeJavaSource(Path sourceRoot, String packageName, String className, String methodName) throws IOException {
        Path packageDir = sourceRoot.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);
        Path javaFile = packageDir.resolve(className + ".java");
        Files.writeString(javaFile, """
                package %s;
                public class %s {
                  public int %s() {
                    if (true) { }
                    switch (1) { case 1 -> {} }
                    return 1;
                  }
                }
                """.formatted(packageName, className, methodName));
        return javaFile;
    }

    private static String rootBuildScript() {
        return """
                plugins {
                    id("de.burger.forensics.btmgen")
                }

                btmGen {
                    scanSubprojects.set(true)
                    outputFile.set(layout.buildDirectory.file("forensics/all-modules.btm").get().asFile)
                }
                """;
    }

    private static String simpleBuildScript() {
        return """
                plugins {
                    java
                    id("de.burger.forensics.btmgen")
                }

                java {
                    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
                }
                """;
    }

    private static String defaultJavaSubprojectBuildScript() {
        return """
                plugins {
                    `java-library`
                }
                """;
    }

    private static void writeMonorepoSettings(Path rootDir, String... modules) throws IOException {
        List<String> includes = new ArrayList<>();
        for (String module : modules) {
            Files.createDirectories(rootDir.resolve(module));
            includes.add("\"" + module + "\"");
        }
        Files.writeString(rootDir.resolve("settings.gradle.kts"), """
                rootProject.name = "functional-root"
                include(%s)
                """.formatted(String.join(", ", includes)));
    }

    private static void writeSimpleSettings(Path rootDir) throws IOException {
        Files.writeString(rootDir.resolve("settings.gradle.kts"), "rootProject.name = \"functional-simple\"");
    }

    private static BuildResult runGradle(Path projectDir, String... arguments) {
        List<String> gradleArguments = new ArrayList<>();
        gradleArguments.add("--stacktrace");
        gradleArguments.addAll(List.of(arguments));
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(gradleArguments)
            .build();
    }

    private static TaskOutcome taskOutcome(BuildResult result, String taskPath) {
        assertNotNull(result.task(taskPath), "Expected task " + taskPath + " to be present in the build result");
        return result.task(taskPath).getOutcome();
    }
}
