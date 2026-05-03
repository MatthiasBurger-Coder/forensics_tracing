package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BtmGenerationRunnerTest {

    @Test
    void runnerWritesSingleTemplateRule(@TempDir Path tempDir) throws IOException {
        RecordingStrategy strategy = new RecordingStrategy("CUSTOM");
        Path outputFile = tempDir.resolve("build/forensics/template.btm");
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .outputFile(outputFile)
                .templateRequest(new BtmTemplateRequest("CUSTOM", "com.example.Foo", "bar", "(I)V"))
                .helperFqn(" ")
                .build();

        BtmGenerationResult result = new BtmGenerationRunner(StrategyRegistry.builder().register(strategy).build(),
                NoOpPluginLogPort.INSTANCE).generate(request);

        assertEquals(1, result.generatedRuleCount());
        assertEquals(0, result.scannedFileCount());
        assertEquals(BtmGenerationDefaults.DEFAULT_HELPER_FQN, strategy.calls.get(0).helperFqn());
        assertEquals("(I)V", strategy.calls.get(0).methodDesc());
        assertTrue(Files.readString(outputFile).contains("CUSTOM:com.example.Foo#bar"));
    }

    @Test
    void runnerScansSourceRootsWritesRulesAndProfile(@TempDir Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Sample.java"), """
                package com.example;
                public class Sample {
                  public int run(int value) {
                    if (value > 0) { }
                    return value;
                  }
                }
                """);
        Path outputFile = tempDir.resolve("build/forensics/scanned.btm");
        Path profileReport = tempDir.resolve("build/forensics/scan-profile.json");
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoot(tempDir.resolve("src/main/java"))
                .outputFile(outputFile)
                .cacheDatabaseFile(tempDir.resolve("build/forensics/cache/scan-cache"))
                .profileReportFile(profileReport)
                .cacheEnabled(true)
                .profilingEnabled(true)
                .includePackages(List.of("com.example"))
                .build();
        Map<String, RecordingStrategy> strategies = defaultRecordingStrategies();

        BtmGenerationResult result = new BtmGenerationRunner(toRegistry(strategies), NoOpPluginLogPort.INSTANCE)
                .generate(request);

        assertTrue(Files.exists(outputFile));
        assertTrue(Files.exists(profileReport));
        assertEquals(1, result.scannedFileCount());
        assertEquals(1, result.parsedFileCount());
        assertEquals(1, result.cacheMissCount());
        assertEquals(0, result.cacheHitCount());
        assertFalse(result.validationReport().hasIssues());
        assertTrue(result.generatedRuleCount() > 0);
        assertTrue(Files.readString(outputFile).contains("com.example.Sample#run"));
        assertTrue(Files.readString(profileReport).contains("\"JAVA_PARSER_PARSE\""));
        assertEquals(List.of("run"), strategies.get("RETURN").calls.stream().map(RuleParams::methodName).toList());
    }

    @Test
    void runnerRejectsUnsupportedCacheBackend(@TempDir Path tempDir) {
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoot(tempDir.resolve("src/main/java"))
                .outputFile(tempDir.resolve("build/forensics/rules.btm"))
                .cacheEnabled(true)
                .cacheBackend("sqlite")
                .build();

        BtmGenerationRunner runner = new BtmGenerationRunner();

        BtmGenerationException exception = assertThrows(BtmGenerationException.class, () -> runner.generate(request));

        assertEquals("Unsupported parser scan cache backend: sqlite", exception.getMessage());
    }

    @Test
    void runnerRejectsDependencyAwareInvalidation(@TempDir Path tempDir) {
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoot(tempDir.resolve("src/main/java"))
                .outputFile(tempDir.resolve("build/forensics/rules.btm"))
                .cacheEnabled(true)
                .dependencyAwareInvalidation(true)
                .build();

        BtmGenerationRunner runner = new BtmGenerationRunner();

        BtmGenerationException exception = assertThrows(BtmGenerationException.class, () -> runner.generate(request));

        assertEquals("Dependency-aware cache invalidation is not implemented yet.", exception.getMessage());
    }

    @Test
    void runnerReportsUnresolvedTypeReferencesWithoutFailingByDefault(@TempDir Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Sample.java"), """
                package com.example;
                import org.acme.DeploymentType;
                public class Sample {
                  public boolean run(Object deploymentUnit) {
                    if (DeploymentType.EAR != null) { }
                    return true;
                  }
                }
                """);
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoot(tempDir.resolve("src/main/java"))
                .outputFile(tempDir.resolve("build/forensics/validation.btm"))
                .minBranchesPerMethod(0)
                .build();

        BtmGenerationResult result = new BtmGenerationRunner().generate(request);

        assertTrue(result.validationReport().hasIssues());
        assertEquals(List.of("DeploymentType"),
                result.validationReport().issues().stream().map(issue -> issue.symbol()).toList());
    }

    @Test
    void runnerFailsForUnresolvedTypeReferencesWhenStrictValidationIsEnabled(@TempDir Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Sample.java"), """
                package com.example;
                import org.acme.DeploymentType;
                public class Sample {
                  public boolean run(Object deploymentUnit) {
                    if (DeploymentType.EAR != null) { }
                    return true;
                  }
                }
                """);
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoot(tempDir.resolve("src/main/java"))
                .outputFile(tempDir.resolve("build/forensics/validation.btm"))
                .minBranchesPerMethod(0)
                .strictConditionValidation(true)
                .build();

        BtmGenerationException exception = assertThrows(BtmGenerationException.class,
                () -> new BtmGenerationRunner().generate(request));

        assertTrue(exception.getMessage().contains("Condition validation failed with 1 unresolved type reference warning(s)"));
    }

    @Test
    void runnerDedupesDuplicateRuleHeaders() {
        List<String> deduped = BtmGenerationRunner.dedupeRuleHeaders(List.of(
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
    }

    private static Map<String, RecordingStrategy> defaultRecordingStrategies() {
        return List.of(
                "METHOD_ENTER",
                "METHOD_EXIT",
                "RETURN",
                "THROW",
                "IF_TRUE",
                "IF_FALSE",
                "SWITCH",
                "SWITCH_CASE"
        ).stream().collect(Collectors.toMap(id -> id, RecordingStrategy::new));
    }

    private static StrategyRegistry toRegistry(Map<String, RecordingStrategy> strategies) {
        var builder = StrategyRegistry.builder();
        strategies.values().forEach(builder::register);
        return builder.build();
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
            StringBuilder output = new StringBuilder(id).append(":").append(params.displayName());
            if (params.condition() != null) {
                output.append(":").append(params.condition());
            }
            return output.toString();
        }
    }
}
