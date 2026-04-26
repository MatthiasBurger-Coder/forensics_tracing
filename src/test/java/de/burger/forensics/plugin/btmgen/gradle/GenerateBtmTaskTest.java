package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GenerateBtmTaskTest {

    @Test
    void generateWithMinimalInputsWritesSingleRenderedRule(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Files.createDirectories(tempDir.resolve("src/main/java"));

        var task = project.getTasks().register("generateBtmMinimal", GenerateBtmTask.class).get();

        var extension = newExtension(project);
        var strategy = new RecordingStrategy("CUSTOM");
        extension.setRegistry(StrategyRegistry.builder().register(strategy).build());
        extension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        Path outputFile = tempDir.resolve("build/forensics/minimal.btm");
        extension.getOutputFile().set(outputFile.toFile());

        task.setExtension(extension);
        task.getTemplateId().set("CUSTOM");
        task.getClassName().set("com.example.Foo");
        task.getMethodName().set("bar");
        task.getMethodDesc().set("(I)V");

        task.generate();

        assertEquals(1, strategy.calls.size(), "Expected exactly one rendered rule");
        RuleParams params = strategy.calls.get(0);
        assertEquals("com.example.Foo", params.className());
        assertEquals("bar", params.methodName());
        assertEquals("(I)V", params.methodDesc());
        assertEquals(RuleParams.DEFAULT_HELPER_FQN, params.helperFqn());

        assertTrue(Files.exists(outputFile), "Output file should be created");
        String content = Files.readString(outputFile);
        assertTrue(content.contains("CUSTOM:com.example.Foo#bar"), "Rendered rule should be written to file");
    }

    @Test
    void generateScansSourcesAndRendersAllHeuristicRules(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Path javaFile = srcDir.resolve("Sample.java");
        Files.writeString(javaFile, """
                package com.example;
                public class Sample {
                  public int alpha() {
                    if (true) { }
                    switch (1) { case 1 -> {} }
                    return 1;
                  }
                  public void beta() {
                    if (false) { }
                    switch (2) { case 2 -> {} }
                    throw new IllegalStateException();
                  }
                }
                """);

        var task = project.getTasks().register("generateBtmScan", GenerateBtmTask.class).get();

        var extension = newExtension(project);
        Map<String, RecordingStrategy> strategies = registerDefaultStrategies(extension);
        extension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        Path outputFile = tempDir.resolve("build/forensics/scanned.btm");
        extension.getOutputFile().set(outputFile.toFile());

        task.setExtension(extension);

        task.generate();

        assertTrue(Files.exists(outputFile), "Output file should be created");
        String content = Files.readString(outputFile);

        assertRenderedRuleContent(content);
        assertStrategyInvocations(strategies);
    }

    @Test
    void generateScansMultipleSourceRootsAcrossModules(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        Path moduleASrc = tempDir.resolve("module-a/src/main/java/com/acme/a");
        Path moduleBSrc = tempDir.resolve("module-b/src/main/java/com/acme/b");
        Files.createDirectories(moduleASrc);
        Files.createDirectories(moduleBSrc);

        Files.writeString(moduleASrc.resolve("Alpha.java"), """
                package com.acme.a;
                public class Alpha {
                  public int one() {
                    if (true) { }
                    switch (1) { case 1 -> {} }
                    return 1;
                  }
                }
                """);
        Files.writeString(moduleBSrc.resolve("Beta.java"), """
                package com.acme.b;
                public class Beta {
                  public int two() {
                    if (false) { }
                    switch (2) { case 2 -> {} }
                    return 2;
                  }
                }
                """);

        var task = project.getTasks().register("generateBtmMultiModule", GenerateBtmTask.class).get();
        var extension = newExtension(project);
        extension.getSourceRoots().setFrom(
            tempDir.resolve("module-a/src/main/java").toFile(),
            tempDir.resolve("module-b/src/main/java").toFile()
        );
        Path outputFile = tempDir.resolve("build/forensics/multi-module.btm");
        extension.getOutputFile().set(outputFile.toFile());
        task.setExtension(extension);

        task.generate();

        assertTrue(Files.exists(outputFile), "Output file should be created");
        String content = Files.readString(outputFile);
        assertTrue(content.contains("com.acme.a.Alpha#one"));
        assertTrue(content.contains("com.acme.b.Beta#two"));
    }

    @Test
    void generateIgnoresMissingExplicitSourceRoots(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path validSrc = tempDir.resolve("module-a/src/main/java/com/acme");
        Files.createDirectories(validSrc);
        Files.writeString(validSrc.resolve("Alpha.java"), """
                package com.acme;
                public class Alpha {
                  public int one() {
                    if (true) { }
                    switch (1) { case 1 -> {} }
                    return 1;
                  }
                }
                """);

        var task = project.getTasks().register("generateBtmMissingRoots", GenerateBtmTask.class).get();
        var extension = newExtension(project);
        extension.getSourceRoots().setFrom(
            tempDir.resolve("missing-a/src/main/java").toFile(),
            tempDir.resolve("module-a/src/main/java").toFile(),
            tempDir.resolve("missing-b/src/main/java").toFile()
        );
        Path outputFile = tempDir.resolve("build/forensics/missing-roots.btm");
        extension.getOutputFile().set(outputFile.toFile());
        task.setExtension(extension);

        assertDoesNotThrow(task::generate);

        String content = Files.readString(outputFile);
        assertTrue(content.contains("com.acme.Alpha#one"));
        assertFalse(content.contains("missing-a"));
        assertFalse(content.contains("missing-b"));
    }

    @Test
    void generateTreatsMissingLegacySourceRootAsOptional(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var task = project.getTasks().register("generateBtmMissingLegacyRoot", GenerateBtmTask.class).get();
        var extension = newExtension(project);
        extension.getSourceRoot().set(tempDir.resolve("missing/src/main/java").toFile());
        Path outputFile = tempDir.resolve("build/forensics/missing-legacy-root.btm");
        extension.getOutputFile().set(outputFile.toFile());
        task.setExtension(extension);

        assertDoesNotThrow(task::generate);

        assertTrue(Files.exists(outputFile));
        List<String> lines = Files.readAllLines(outputFile);
        assertEquals("# Generated Byteman rules", lines.get(0));
        assertEquals(2, lines.size());
    }

    @Test
    void generateAutoDiscoversSubprojectSourceSetRoots(@TempDir Path tempDir) throws IOException {
        Path rootDir = tempDir.resolve("root");
        Path moduleDir = rootDir.resolve("module-a");
        Files.createDirectories(rootDir);
        Files.createDirectories(moduleDir);

        var rootProject = ProjectBuilder.builder().withProjectDir(rootDir.toFile()).build();
        var moduleProject = ProjectBuilder.builder().withParent(rootProject).withName("module-a").withProjectDir(moduleDir.toFile()).build();
        moduleProject.getPlugins().apply("java-library");

        Path moduleSrc = moduleDir.resolve("src/main/java/com/acme/module");
        Files.createDirectories(moduleSrc);

        Files.writeString(moduleSrc.resolve("ModuleSample.java"), """
                package com.acme.module;
                public class ModuleSample {
                  public int moduleMethod() {
                    if (false) { }
                    switch (2) { case 2 -> {} }
                    return 2;
                  }
                }
                """);

        var task = rootProject.getTasks().register("generateBtmAutoModules", GenerateBtmTask.class).get();
        var extension = newExtension(rootProject);
        extension.getScanSubprojects().set(true);
        Path outputFile = rootDir.resolve("build/forensics/auto-modules.btm");
        extension.getOutputFile().set(outputFile.toFile());
        task.setExtension(extension);

        task.generate();

        assertTrue(Files.exists(outputFile), "Output file should be created");
        String content = Files.readString(outputFile);
        assertTrue(content.contains("com.acme.module.ModuleSample#moduleMethod"));
    }

    @Test
    void generateDiscoversCustomSubprojectMainSourceSetDirectories(@TempDir Path tempDir) throws IOException {
        Path rootDir = tempDir.resolve("root");
        Path moduleDir = rootDir.resolve("module-a");
        Files.createDirectories(moduleDir);

        var rootProject = ProjectBuilder.builder().withProjectDir(rootDir.toFile()).build();
        var moduleProject = ProjectBuilder.builder().withParent(rootProject).withName("module-a").withProjectDir(moduleDir.toFile()).build();
        moduleProject.getPlugins().apply("java-library");
        mainSourceSets(moduleProject).getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava().setSrcDirs(List.of("sources/main/java"));

        Path customSrc = moduleDir.resolve("sources/main/java/com/acme/module");
        Files.createDirectories(customSrc);
        Files.writeString(customSrc.resolve("CustomSample.java"), """
                package com.acme.module;
                public class CustomSample {
                  public int moduleMethod() {
                    if (true) { }
                    switch (1) { case 1 -> {} }
                    return 1;
                  }
                }
                """);

        var task = rootProject.getTasks().register("generateBtmCustomSourceSet", GenerateBtmTask.class).get();
        var extension = newExtension(rootProject);
        extension.getScanSubprojects().set(true);
        Path outputFile = rootDir.resolve("build/forensics/custom-source-set.btm");
        extension.getOutputFile().set(outputFile.toFile());
        task.setExtension(extension);

        task.generate();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("com.acme.module.CustomSample#moduleMethod"));
    }

    @Test
    void resolveSourceRootsCombinesExplicitRootsWithAutoDiscoveredSubprojects(@TempDir Path tempDir) throws Exception {
        Path rootDir = tempDir.resolve("root");
        Path explicitRoot = tempDir.resolve("external/src/main/java");
        Path moduleDir = rootDir.resolve("module-a");
        Path moduleRoot = moduleDir.resolve("src/main/java");
        Files.createDirectories(explicitRoot);
        Files.createDirectories(moduleRoot);

        var rootProject = ProjectBuilder.builder().withProjectDir(rootDir.toFile()).build();
        var moduleProject = ProjectBuilder.builder().withParent(rootProject).withName("module-a").withProjectDir(moduleDir.toFile()).build();
        moduleProject.getPlugins().apply("java-library");

        var task = rootProject.getTasks().register("generateBtmCombinedRoots", GenerateBtmTask.class).get();
        var extension = newExtension(rootProject);
        extension.getSourceRoots().setFrom(explicitRoot.toFile());
        extension.getScanSubprojects().set(true);
        task.setExtension(extension);

        Method resolveSourceRoots = GenerateBtmTask.class.getDeclaredMethod("resolveSourceRoots");
        resolveSourceRoots.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Path> roots = (List<Path>) resolveSourceRoots.invoke(task);

        assertEquals(List.of(explicitRoot.toAbsolutePath().normalize(), moduleRoot.toAbsolutePath().normalize()), roots);
    }

    @Test
    void resolveSourceRootsDeduplicatesDuplicateRootsAcrossInputs(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);

        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        project.getPlugins().apply("java-library");
        var task = project.getTasks().register("generateBtmDedupRoots", GenerateBtmTask.class).get();
        var extension = newExtension(project);
        extension.getSourceRoot().set(sourceRoot.toFile());
        extension.getSourceRoots().setFrom(sourceRoot.toFile(), tempDir.resolve("src/main/java").toFile());
        task.setExtension(extension);

        Method resolveSourceRoots = GenerateBtmTask.class.getDeclaredMethod("resolveSourceRoots");
        resolveSourceRoots.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Path> roots = (List<Path>) resolveSourceRoots.invoke(task);

        assertEquals(List.of(sourceRoot.toAbsolutePath().normalize()), roots);
    }

    @Test
    void generatedRulesInvokeHelpersDirectly(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Path javaFile = srcDir.resolve("Sample.java");
        Files.writeString(javaFile, """
                package com.example;
                public class Sample {
                  public int alpha() {
                    if (true) { }
                    switch (1) { case 1 -> {} }
                    return 1;
                  }
                  public void beta() {
                    if (false) { }
                    switch (2) { case 2 -> {} }
                    throw new IllegalStateException();
                  }
                }
                """);

        Path scanOutput = tempDir.resolve("build/forensics/helpers-scan.btm");
        var scanTask = project.getTasks().register("generateBtmHelpersScan", GenerateBtmTask.class).get();
        var scanExtension = newExtension(project);
        scanExtension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        scanExtension.getOutputFile().set(scanOutput.toFile());
        scanTask.setExtension(scanExtension);

        scanTask.generate();

        assertTrue(Files.exists(scanOutput));
        String scanContent = Files.readString(scanOutput);
        assertFalse(scanContent.contains("helper()."));
        assertTrue(scanContent.contains("onEnter("));
        assertTrue(scanContent.contains("onExit("));
        assertTrue(scanContent.contains("onBranch("));
        assertTrue(scanContent.contains("onSwitch("));
        assertTrue(scanContent.contains("onCase("));
        assertTrue(scanContent.contains("onException("));

        Path jdbcOutput = tempDir.resolve("build/forensics/helpers-jdbc.btm");
        var jdbcTask = project.getTasks().register("generateBtmHelpersJdbc", GenerateBtmTask.class).get();
        var jdbcExtension = newExtension(project);
        jdbcExtension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        jdbcExtension.getOutputFile().set(jdbcOutput.toFile());
        jdbcTask.setExtension(jdbcExtension);
        jdbcTask.getTemplateId().set("JDBC_EXECUTE");
        jdbcTask.getClassName().set("java.sql.Statement");
        jdbcTask.getMethodName().set("execute");

        jdbcTask.generate();

        assertTrue(Files.exists(jdbcOutput));
        String jdbcContent = Files.readString(jdbcOutput);
        assertFalse(jdbcContent.contains("helper()."));
        assertTrue(jdbcContent.contains("ioBegin("));
        assertTrue(jdbcContent.contains("ioEnd("));

        Path threadOutput = tempDir.resolve("build/forensics/helpers-thread.btm");
        var threadTask = project.getTasks().register("generateBtmHelpersThread", GenerateBtmTask.class).get();
        var threadExtension = newExtension(project);
        threadExtension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        threadExtension.getOutputFile().set(threadOutput.toFile());
        threadTask.setExtension(threadExtension);
        threadTask.getTemplateId().set("THREAD_LIFECYCLE");
        threadTask.getClassName().set("java.lang.Thread");
        threadTask.getMethodName().set("start");

        threadTask.generate();

        assertTrue(Files.exists(threadOutput));
        String threadContent = Files.readString(threadOutput);
        assertFalse(threadContent.contains("helper()."));
        assertTrue(threadContent.contains("threadFork("));
        assertTrue(threadContent.contains("threadJoin("));
    }

    @Test
    void generateRespectsCustomHelperFqn(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Files.createDirectories(tempDir.resolve("src/main/java"));

        var task = project.getTasks().register("generateBtmCustomHelper", GenerateBtmTask.class).get();

        var extension = newExtension(project);
        var strategy = new RecordingStrategy("CUSTOM");
        extension.setRegistry(StrategyRegistry.builder().register(strategy).build());
        extension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        Path outputFile = tempDir.resolve("build/forensics/custom-helper.btm");
        extension.getOutputFile().set(outputFile.toFile());
        extension.getHelperFqn().set("com.example.Helper");

        task.setExtension(extension);
        task.getTemplateId().set("CUSTOM");
        task.getClassName().set("com.example.Foo");
        task.getMethodName().set("bar");

        task.generate();

        assertEquals("com.example.Helper", strategy.calls.get(0).helperFqn());
    }

    @Test
    void generateNormalizesBlankHelperFqn(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Files.createDirectories(tempDir.resolve("src/main/java"));

        var task = project.getTasks().register("generateBtmBlankHelper", GenerateBtmTask.class).get();

        var extension = newExtension(project);
        var strategy = new RecordingStrategy("CUSTOM");
        extension.setRegistry(StrategyRegistry.builder().register(strategy).build());
        extension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        Path outputFile = tempDir.resolve("build/forensics/blank-helper.btm");
        extension.getOutputFile().set(outputFile.toFile());
        extension.getHelperFqn().set("   ");

        task.setExtension(extension);
        task.getTemplateId().set("CUSTOM");
        task.getClassName().set("com.example.Foo");
        task.getMethodName().set("bar");

        task.generate();

        assertEquals(RuleParams.DEFAULT_HELPER_FQN, strategy.calls.get(0).helperFqn());
    }

    @Test
    void generateDedupesDuplicateRuleHeaders(@TempDir Path tempDir) throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Sample.java"), """
                package com.example;
                public class Sample {
                  public void alpha() {
                    if (true) { }
                    if (false) { }
                  }
                }
                """);

        var task = project.getTasks().register("generateBtmDedupHeaders", GenerateBtmTask.class).get();
        var extension = newExtension(project);
        extension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        extension.getMinBranchesPerMethod().set(0);
        Path outputFile = tempDir.resolve("build/forensics/dedup-headers.btm");
        extension.getOutputFile().set(outputFile.toFile());
        extension.setRegistry(StrategyRegistry.builder()
            .register(new RuleRenderStrategy() {
                @Override
                public String id() {
                    return "IF_TRUE";
                }

                @Override
                public String render(RuleParams params) {
                    return """
                        RULE duplicate-header
                        CLASS com.example.Sample
                        METHOD alpha
                        HELPER %s
                        AT ENTRY
                        IF %s
                        DO
                            onBranch(com.example.Sample.class, "alpha", "IF_TRUE");
                        ENDRULE
                        """.formatted(params.helperFqn(), params.condition());
                }
            })
            .register(new RuleRenderStrategy() {
                @Override
                public String id() {
                    return "IF_FALSE";
                }

                @Override
                public String render(RuleParams params) {
                    return """
                        RULE unique-false-%s
                        CLASS com.example.Sample
                        METHOD alpha
                        HELPER %s
                        AT ENTRY
                        IF %s
                        DO
                            onBranch(com.example.Sample.class, "alpha", "IF_FALSE");
                        ENDRULE
                        """.formatted(params.id(), params.helperFqn(), params.condition());
                }
            })
            .build());
        task.setExtension(extension);
        task.getIncludeEntryExit().set(false);

        task.generate();

        String content = Files.readString(outputFile);
        assertTrue(content.contains("RULE duplicate-header\n"));
        assertTrue(content.contains("RULE duplicate-header_2\n"));
    }

    @Test
    void generateUsesDefaultTaskConventionsWhenExtensionIsNotInjected(@TempDir Path tempDir) {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var task = project.getTasks().register("generateBtmDefaultExtension", GenerateBtmTask.class).get();
        task.getTemplateId().set("METHOD_ENTER");
        task.getClassName().set("com.example.Foo");
        task.getMethodName().set("bar");

        task.generate();

        assertTrue(Files.exists(tempDir.resolve("build/forensics/forensics.btm")));
    }

    @Test
    void activeRegistryFallsBackToBuiltInStrategiesWhenOnlyTheDefaultFingerprintRemains() throws Exception {
        var project = ProjectBuilder.builder().build();
        var task = project.getTasks().register("generateBtmRegistryFallback", GenerateBtmTask.class).get();

        var registryField = GenerateBtmTask.class.getDeclaredField("registry");
        registryField.setAccessible(true);
        registryField.set(task, null);

        Method activeRegistry = GenerateBtmTask.class.getDeclaredMethod("activeRegistry");
        activeRegistry.setAccessible(true);

        StrategyRegistry restoredRegistry = (StrategyRegistry) activeRegistry.invoke(task);
        assertEquals(StrategyRegistries.defaultRegistry().ids(), restoredRegistry.ids());
    }

    @Test
    void activeRegistryRejectsCustomFingerprintsWhenRegistryStateCannotBeRestored() throws Exception {
        var project = ProjectBuilder.builder().build();
        var task = project.getTasks().register("generateBtmCustomRegistryRestore", GenerateBtmTask.class).get();
        task.getRegistryFingerprint().set("CUSTOM=com.example.CustomStrategy");

        var registryField = GenerateBtmTask.class.getDeclaredField("registry");
        registryField.setAccessible(true);
        registryField.set(task, null);

        Method activeRegistry = GenerateBtmTask.class.getDeclaredMethod("activeRegistry");
        activeRegistry.setAccessible(true);

        Exception thrown = assertThrows(Exception.class, () -> activeRegistry.invoke(task));
        assertInstanceOf(GradleException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains(
                "Custom StrategyRegistry instances are not supported when the configuration cache restores this task."
        ));
    }

    @Test
    void privateHelpersCoverFallbackConfigurationBranches(@TempDir Path tempDir) throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        project.getPlugins().apply("java-library");
        var subprojectDir = tempDir.resolve("module-a");
        Files.createDirectories(subprojectDir);
        var subproject = ProjectBuilder.builder().withParent(project).withName("module-a").withProjectDir(subprojectDir.toFile()).build();
        subproject.getPlugins().apply("java-library");
        var task = project.getTasks().register("generateBtmPrivateHelpers", GenerateBtmTask.class).get();
        var extension = newExtension(project);
        extension.getIncludes().set("com.example , org.example");
        extension.getScanSubprojects().set(true);
        task.setExtension(extension);

        Method hasMinimalInputs = GenerateBtmTask.class.getDeclaredMethod("hasMinimalInputs");
        hasMinimalInputs.setAccessible(true);
        Method templateIdOrDefault = GenerateBtmTask.class.getDeclaredMethod("templateIdOrDefault");
        templateIdOrDefault.setAccessible(true);
        Method resolveHelperFqn = GenerateBtmTask.class.getDeclaredMethod("resolveHelperFqn");
        resolveHelperFqn.setAccessible(true);
        Method includeEntryExit = GenerateBtmTask.class.getDeclaredMethod("includeEntryExit");
        includeEntryExit.setAccessible(true);
        Method minBranches = GenerateBtmTask.class.getDeclaredMethod("minBranches");
        minBranches.setAccessible(true);
        Method resolveSourceRoots = GenerateBtmTask.class.getDeclaredMethod("resolveSourceRoots");
        resolveSourceRoots.setAccessible(true);
        Method packagePrefixes = GenerateBtmTask.class.getDeclaredMethod("packagePrefixes");
        packagePrefixes.setAccessible(true);
        Method dedupeRuleHeaders = GenerateBtmTask.class.getDeclaredMethod("dedupeRuleHeaders", List.class);
        dedupeRuleHeaders.setAccessible(true);

        assertEquals("METHOD_ENTER", templateIdOrDefault.invoke(task));
        task.getTemplateId().set(" ");
        assertEquals("METHOD_ENTER", templateIdOrDefault.invoke(task));
        task.getHelperFqn().set(" ");
        assertEquals(RuleParams.DEFAULT_HELPER_FQN, resolveHelperFqn.invoke(task));
        assertEquals(true, includeEntryExit.invoke(task));
        assertEquals(2, minBranches.invoke(task));
        assertEquals(false, hasMinimalInputs.invoke(task));
        task.getClassName().set("com.example.Foo");
        task.getMethodName().set("bar");
        task.getTemplateId().set("CUSTOM");
        assertEquals(true, hasMinimalInputs.invoke(task));

        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.createDirectories(tempDir.resolve("module-a/src/main/java"));
        @SuppressWarnings("unchecked")
        List<Path> roots = (List<Path>) resolveSourceRoots.invoke(task);
        assertEquals(2, roots.size());
        assertTrue(roots.stream().allMatch(path -> path.endsWith("src\\main\\java") || path.endsWith("src/main/java")));
        assertEquals(List.of("com.example", "org.example"), packagePrefixes.invoke(task));

        @SuppressWarnings("unchecked")
        List<String> deduped = (List<String>) dedupeRuleHeaders.invoke(task, List.of(
            "plain text",
            "RULE duplicate\nCLASS A\nENDRULE",
            "RULE duplicate\nCLASS A\nENDRULE",
            "\n  RULE duplicate  \nCLASS A\nENDRULE",
            "RULE    \nCLASS A\nENDRULE",
            "RULER duplicate\nCLASS A\nENDRULE"
        ));
        assertEquals("plain text", deduped.get(0));
        assertTrue(deduped.get(2).contains("RULE duplicate_2"));
        assertTrue(deduped.get(3).contains("RULE duplicate_3"));
        assertEquals("RULE    \nCLASS A\nENDRULE", deduped.get(4));
        assertEquals("RULER duplicate\nCLASS A\nENDRULE", deduped.get(5));

        task.getIncludes().set("");
        assertEquals(List.of(), packagePrefixes.invoke(task));
    }

    private static Map<String, RecordingStrategy> registerDefaultStrategies(BtmGenExtension extension) {
        Map<String, RecordingStrategy> strategies = new HashMap<>();
        var builder = StrategyRegistry.builder();
        for (String id : List.of(
                "METHOD_ENTER",
                "METHOD_EXIT",
                "RETURN",
                "THROW",
                "IF_TRUE",
                "IF_FALSE",
                "SWITCH",
                "SWITCH_CASE"
        )) {
            var strategy = new RecordingStrategy(id);
            strategies.put(id, strategy);
            builder.register(strategy);
        }
        extension.setRegistry(builder.build());
        return strategies;
    }

    private static BtmGenExtension newExtension(org.gradle.api.Project project) {
        return project.getObjects().newInstance(BtmGenExtension.class);
    }

    private static SourceSetContainer mainSourceSets(org.gradle.api.Project project) {
        return project.getExtensions().getByType(SourceSetContainer.class);
    }

    private static void assertRenderedRuleContent(String content) {
        assertTrue(content.contains("METHOD_ENTER:com.example.Sample#alpha"));
        assertTrue(content.contains("METHOD_ENTER:com.example.Sample#beta"));
        assertTrue(content.contains("METHOD_EXIT:com.example.Sample#alpha"));
        assertTrue(content.contains("METHOD_EXIT:com.example.Sample#beta"));
        assertTrue(content.contains("RETURN:com.example.Sample#alpha"));
        assertTrue(content.contains("THROW:com.example.Sample#beta"));
        assertTrue(content.contains("IF_TRUE:com.example.Sample#alpha:true"));
        assertTrue(content.contains("IF_FALSE:com.example.Sample#alpha:true"));
        assertTrue(content.contains("IF_TRUE:com.example.Sample#beta:false"));
        assertTrue(content.contains("IF_FALSE:com.example.Sample#beta:false"));
        assertTrue(content.contains("SWITCH:com.example.Sample#alpha:1"));
        assertTrue(content.contains("SWITCH:com.example.Sample#beta:2"));
        assertTrue(content.contains("SWITCH_CASE:1"));
        assertTrue(content.contains("SWITCH_CASE:2"));
    }

    private static void assertStrategyInvocations(Map<String, RecordingStrategy> strategies) {
        assertEquals(2, strategies.get("METHOD_ENTER").calls.size());
        assertEquals(2, strategies.get("METHOD_EXIT").calls.size());
        assertEquals(1, strategies.get("RETURN").calls.size());
        assertEquals("alpha", strategies.get("RETURN").calls.get(0).methodName());
        assertEquals(1, strategies.get("THROW").calls.size());
        assertEquals("beta", strategies.get("THROW").calls.get(0).methodName());

        assertEquals(Set.of("alpha", "beta"),
                strategies.get("IF_TRUE").calls.stream().map(RuleParams::methodName).collect(Collectors.toSet()));
        assertEquals(List.of("true", "false"),
                strategies.get("IF_TRUE").calls.stream().map(RuleParams::condition).toList());
        assertEquals(List.of(4, 9),
                strategies.get("IF_TRUE").calls.stream().map(RuleParams::sourceLine).toList());
        assertEquals(List.of("true", "false"),
                strategies.get("IF_FALSE").calls.stream().map(RuleParams::condition).toList());
        assertEquals(List.of(4, 9),
                strategies.get("IF_FALSE").calls.stream().map(RuleParams::sourceLine).toList());
        assertEquals(2, strategies.get("SWITCH").calls.size());
        assertEquals(2, strategies.get("SWITCH_CASE").calls.size());
        assertEquals(List.of("1", "2"),
                strategies.get("SWITCH_CASE").calls.stream().map(RuleParams::displayName).toList());

        strategies.values().forEach(recording ->
                recording.calls.forEach(params ->
                        assertEquals(RuleParams.DEFAULT_HELPER_FQN, params.helperFqn())));
    }

    private static final class RecordingStrategy implements RuleRenderStrategy {
        private final String id;
        private final List<RuleParams> calls = new ArrayList<>();

        private RecordingStrategy(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String render(RuleParams params) {
            calls.add(params);
            StringBuilder sb = new StringBuilder();
            sb.append(id).append(":").append(params.displayName());
            if (params.condition() != null) {
                sb.append(":").append(params.condition());
            }
            return sb.toString();
        }
    }
}
