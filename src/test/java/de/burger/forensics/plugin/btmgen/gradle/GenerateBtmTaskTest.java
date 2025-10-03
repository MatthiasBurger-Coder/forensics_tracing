package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

        var extension = project.getObjects().newInstance(BtmGenExtension.class);
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
        Files.writeString(javaFile, "package com.example;\n" +
                "public class Sample {\n" +
                "  public int alpha() {\n" +
                "    if (true) { }\n" +
                "    switch (1) { case 1 -> {} }\n" +
                "    return 1;\n" +
                "  }\n" +
                "  public void beta() {\n" +
                "    if (false) { }\n" +
                "    switch (2) { case 2 -> {} }\n" +
                "    throw new IllegalStateException();\n" +
                "  }\n" +
                "}\n");

        var task = project.getTasks().register("generateBtmScan", GenerateBtmTask.class).get();

        var extension = project.getObjects().newInstance(BtmGenExtension.class);
        Map<String, RecordingStrategy> strategies = registerDefaultStrategies(extension);
        extension.getSourceRoot().set(tempDir.resolve("src/main/java").toFile());
        Path outputFile = tempDir.resolve("build/forensics/scanned.btm");
        extension.getOutputFile().set(outputFile.toFile());

        task.setExtension(extension);

        task.generate();

        assertTrue(Files.exists(outputFile), "Output file should be created");
        String content = Files.readString(outputFile);

        assertTrue(content.contains("METHOD_ENTER:com.example.Sample#alpha"));
        assertTrue(content.contains("METHOD_ENTER:com.example.Sample#beta"));
        assertTrue(content.contains("METHOD_EXIT:com.example.Sample#alpha"));
        assertTrue(content.contains("METHOD_EXIT:com.example.Sample#beta"));
        assertTrue(content.contains("RETURN:com.example.Sample#alpha"));
        assertTrue(content.contains("THROW:com.example.Sample#beta"));
        assertTrue(content.contains("IF_TRUE:com.example.Sample#alpha:true"));
        assertTrue(content.contains("IF_FALSE:com.example.Sample#alpha:false"));
        assertTrue(content.contains("IF_TRUE:com.example.Sample#beta:true"));
        assertTrue(content.contains("IF_FALSE:com.example.Sample#beta:false"));
        assertTrue(content.contains("SWITCH:com.example.Sample#alpha"));
        assertTrue(content.contains("SWITCH:com.example.Sample#beta"));
        assertTrue(content.contains("SWITCH_CASE:com.example.Sample#alpha"));
        assertTrue(content.contains("SWITCH_CASE:com.example.Sample#beta"));

        assertEquals(2, strategies.get("METHOD_ENTER").calls.size());
        assertEquals(2, strategies.get("METHOD_EXIT").calls.size());
        assertEquals(1, strategies.get("RETURN").calls.size());
        assertEquals("alpha", strategies.get("RETURN").calls.get(0).methodName());
        assertEquals(1, strategies.get("THROW").calls.size());
        assertEquals("beta", strategies.get("THROW").calls.get(0).methodName());

        assertEquals(Set.of("alpha", "beta"),
                strategies.get("IF_TRUE").calls.stream().map(RuleParams::methodName).collect(Collectors.toSet()));
        assertEquals(List.of("true", "true"),
                strategies.get("IF_TRUE").calls.stream().map(RuleParams::condition).toList());
        assertEquals(List.of("false", "false"),
                strategies.get("IF_FALSE").calls.stream().map(RuleParams::condition).toList());
        assertEquals(2, strategies.get("SWITCH").calls.size());
        assertEquals(2, strategies.get("SWITCH_CASE").calls.size());
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
