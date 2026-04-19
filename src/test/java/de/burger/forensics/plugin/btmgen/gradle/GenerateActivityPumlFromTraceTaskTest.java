package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerateActivityPumlFromTraceTaskTest {

    @Test
    void generateWritesTraceActivityDiagram(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("trace.json");
        Files.writeString(input, String.join(System.lineSeparator(), List.of(
            "{\"@ts\":\"2026-01-01T00:00:00Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"label\":\"rule-1:orderId != null\",\"value\":\"true\"}}",
            "{\"@ts\":\"2026-01-01T00:00:01Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.OrderService\",\"method\":\"handle\",\"kind\":\"if\",\"branch\":\"IF_TRUE\"}}",
            "{\"@ts\":\"2026-01-01T00:00:02Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.OrderService\",\"method\":\"handle\",\"result\":\"ok\"}}",
            "{\"@ts\":\"2026-01-01T00:00:03Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"worker\",\"details\":{\"class\":\"com.example.Other\",\"method\":\"skip\",\"result\":\"nope\"}}"
        )));
        Path output = tempDir.resolve("build/forensics/trace-activity.puml");
        GenerateActivityPumlFromTraceTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("generateActivityFromTrace", GenerateActivityPumlFromTraceTask.class)
            .get();
        task.getInputTrace().set(input.toFile());
        task.getOutputPuml().set(output.toFile());

        task.generate();

        String rendered = Files.readString(output);
        assertThat(rendered).contains("@startuml");
        assertThat(rendered).contains("|OrderService|");
        assertThat(rendered).contains("if (orderId != null) then (true)");
        assertThat(rendered).contains(":IF_TRUE taken (observed=true);");
        assertThat(rendered).contains(":handle() -> ok;");
        assertThat(rendered).contains("Trace limits:");
    }

    @Test
    void generateRejectsMissingOrEmptyTraceInputs(@TempDir Path tempDir) throws IOException {
        GenerateActivityPumlFromTraceTask missingTask = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("missingTrace", GenerateActivityPumlFromTraceTask.class)
            .get();
        missingTask.getInputTrace().set(tempDir.resolve("missing.json").toFile());
        missingTask.getOutputPuml().set(tempDir.resolve("out.puml").toFile());

        assertThatThrownBy(missingTask::generate)
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("Trace input not found");

        Path empty = tempDir.resolve("empty.json");
        Files.writeString(empty, System.lineSeparator());
        GenerateActivityPumlFromTraceTask emptyTask = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("emptyTrace", GenerateActivityPumlFromTraceTask.class)
            .get();
        emptyTask.getInputTrace().set(empty.toFile());
        emptyTask.getOutputPuml().set(tempDir.resolve("empty-out.puml").toFile());

        assertThatThrownBy(emptyTask::generate)
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("No trace events found");
    }

    @Test
    void generateRejectsExplicitEndpointsWithoutMethodExit(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("trace.json");
        Files.writeString(input,
            "{\"@ts\":\"2026-01-01T00:00:00Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.Service\",\"method\":\"run\",\"kind\":\"if\",\"branch\":\"IF_FALSE\"}}");
        GenerateActivityPumlFromTraceTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("missingEndpoint", GenerateActivityPumlFromTraceTask.class)
            .get();
        task.getInputTrace().set(input.toFile());
        task.getOutputPuml().set(tempDir.resolve("out.puml").toFile());
        task.getRootClass().set("com.example.Service");
        task.getRootMethod().set("run");

        assertThatThrownBy(task::generate)
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("No METHOD_EXIT endpoint found");
    }

    @Test
    void generateRejectsTracesWithoutMethodExitEvents(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("trace.json");
        Files.writeString(input,
            "{\"@ts\":\"2026-01-01T00:00:00Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"label\":\"rule-1:flag\",\"value\":\"true\"}}");
        GenerateActivityPumlFromTraceTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("noMethodExit", GenerateActivityPumlFromTraceTask.class)
            .get();
        task.getInputTrace().set(input.toFile());
        task.getOutputPuml().set(tempDir.resolve("out.puml").toFile());

        assertThatThrownBy(task::generate)
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("Trace does not contain METHOD_EXIT events");
    }

    @Test
    void generateUsesFallbackConditionAndFalseBranchRendering(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("trace.json");
        Files.writeString(input, String.join(System.lineSeparator(), List.of(
            "{\"@ts\":\"2026-01-01T00:00:00Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.Job\",\"method\":\"run\",\"kind\":\"if\",\"branch\":\"IF_FALSE\"}}",
            "{\"@ts\":\"2026-01-01T00:00:01Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.Job\",\"method\":\"run\",\"result\":\" \"}}"
        )));
        Path output = tempDir.resolve("build/trace-fallback.puml");
        GenerateActivityPumlFromTraceTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("fallbackTrace", GenerateActivityPumlFromTraceTask.class)
            .get();
        task.getInputTrace().set(input.toFile());
        task.getOutputPuml().set(output.toFile());
        task.getRootClass().set("com.example.Job");
        task.getRootMethod().set("run");

        task.generate();

        String rendered = Files.readString(output);
        assertThat(rendered).contains("if (com.example.Job#run) then (true)");
        assertThat(rendered).contains(":IF_FALSE taken;");
        assertThat(rendered).contains(":run();");
    }

    @Test
    void generateSelectsOnlyTheLatestExplicitEndpointSegment(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("trace.json");
        Files.writeString(input, String.join(System.lineSeparator(), List.of(
            "{\"@ts\":\"2026-01-01T00:00:00Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.Job\",\"method\":\"run\",\"result\":\"first\"}}",
            "{\"@ts\":\"2026-01-01T00:00:01Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"label\":\"rule-2:latest\",\"value\":\"true\"}}",
            "{\"@ts\":\"2026-01-01T00:00:02Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.Job\",\"method\":\"run\",\"kind\":\"if\",\"branch\":\"IF_TRUE\"}}",
            "{\"@ts\":\"2026-01-01T00:00:03Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.Job\",\"method\":\"run\",\"result\":\"second\"}}"
        )));
        Path output = tempDir.resolve("build/latest-segment.puml");
        GenerateActivityPumlFromTraceTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("latestTrace", GenerateActivityPumlFromTraceTask.class)
            .get();
        task.getInputTrace().set(input.toFile());
        task.getOutputPuml().set(output.toFile());
        task.getRootClass().set("com.example.Job");
        task.getRootMethod().set("run");

        task.generate();

        String rendered = Files.readString(output);
        assertThat(rendered).contains(":run() -> second;");
        assertThat(rendered).doesNotContain(":run() -> first;");
        assertThat(rendered).contains("if (latest) then (true)");
    }

    @Test
    void generateFallsBackToLatestNonServiceExitAndSkipsIncompleteEvents(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("trace.json");
        Files.writeString(input, String.join(System.lineSeparator(), List.of(
            "{\"@ts\":\"2026-01-01T00:00:00Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"value\":\"ignored\"}}",
            "{\"@ts\":\"2026-01-01T00:00:01Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.Worker\"}}",
            "{\"@ts\":\"2026-01-01T00:00:02Z\",\"event\":\"BRANCH_TAKEN\",\"thread\":\"main\",\"details\":{\"kind\":\"if\",\"branch\":\"IF_TRUE\"}}",
            "{\"@ts\":\"2026-01-01T00:00:03Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\",\"details\":{\"class\":\"com.example.Worker\",\"method\":\"finish\"}}"
        )));
        Path output = tempDir.resolve("build/non-service-trace.puml");
        GenerateActivityPumlFromTraceTask task = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
            .getTasks()
            .register("nonServiceTrace", GenerateActivityPumlFromTraceTask.class)
            .get();
        task.getInputTrace().set(input.toFile());
        task.getOutputPuml().set(output.toFile());
        task.getRootClass().set("com.example.Worker");
        task.getRootMethod().set(" ");

        task.generate();

        String rendered = Files.readString(output);
        assertThat(rendered).contains("|Worker|");
        assertThat(rendered).contains("if (com.example.Worker#unknown) then (true)");
        assertThat(rendered).contains(":IF_TRUE taken;");
        assertThat(rendered).contains(":finish();");
        assertThat(rendered).doesNotContain(":null();");
        assertThat(rendered).doesNotContain("observed=ignored");
    }

    @Test
    void privateTraceParsingHelpersHandleInvalidInputAndFallbackBranches(@TempDir Path tempDir) throws Exception {
        Method parseLine = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("parseLine", String.class);
        parseLine.setAccessible(true);
        Method readEvents = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("readEvents", Path.class);
        readEvents.setAccessible(true);
        Method selectThread = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("selectThread", List.class);
        selectThread.setAccessible(true);
        Method appendIfBlock = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod(
            "appendIfBlock",
            StringBuilder.class,
            String.class,
            String.class,
            String.class,
            String.class,
            double.class,
            String.class
        );
        appendIfBlock.setAccessible(true);
        Method normalizeLabel = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("normalizeLabel", String.class);
        normalizeLabel.setAccessible(true);
        Method simpleName = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("simpleName", String.class);
        simpleName.setAccessible(true);
        Method escape = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("escape", String.class);
        escape.setAccessible(true);

        assertThat(parseLine.invoke(null, "{\"event\":\"METHOD_EXIT\"}").toString()).isEqualTo("Optional.empty");
        assertThat(parseLine.invoke(null, "{\"@ts\":\"bad\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\"}").toString()).isEqualTo("Optional.empty");
        assertThat(parseLine.invoke(null, "{\"@ts\":\"2026-01-01T00:00:00Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\"}").toString())
            .startsWith("Optional[");

        String repeatedPayload = ("value\\\\with\\\"escapes\\n").repeat(2048);
        @SuppressWarnings("unchecked")
        Optional<Object> parsedLine = (Optional<Object>) parseLine.invoke(
            null,
            "{\"@ts\":\"2026-01-01T00:00:00Z\",\"event\":\"METHOD_EXIT\",\"thread\":\"main\",\"details\":"
                + "{\"class\":\"com.example.Worker\",\"method\":\"finish\",\"result\":\"" + repeatedPayload + "\"}}"
        );
        assertThat(parsedLine).isPresent();

        Method detailsAccessor = parsedLine.orElseThrow().getClass().getDeclaredMethod("details");
        detailsAccessor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) detailsAccessor.invoke(parsedLine.orElseThrow());
        assertThat(details)
            .containsEntry("class", "com.example.Worker")
            .containsEntry("method", "finish")
            .containsEntry("result", ("value\\with\"escapes\n").repeat(2048));

        assertThat(normalizeLabel.invoke(null, "rule-1:flag")).isEqualTo("flag");
        assertThat(normalizeLabel.invoke(null, "flag")).isEqualTo("flag");
        assertThat(simpleName.invoke(null, " ")).isEqualTo("Unknown");
        assertThat(simpleName.invoke(null, "Simple")).isEqualTo("Simple");
        assertThat(escape.invoke(null, "A\"B\r\nC")).isEqualTo("A'B  C");
        assertThat(escape.invoke(null, new Object[] { null })).isEqualTo("");

        StringBuilder trueBranch = new StringBuilder();
        appendIfBlock.invoke(null, trueBranch, "Lane", "flag", "IF_TRUE", " ", 1.5d, "2026-01-01T00:00:00Z");
        assertThat(trueBranch.toString()).contains(":IF_TRUE taken;").doesNotContain("observed=");

        Throwable selectThreadFailure = catchThrowable(() -> selectThread.invoke(null, List.of()));
        assertThat(selectThreadFailure).isInstanceOf(InvocationTargetException.class);
        assertThat(selectThreadFailure.getCause())
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("Could not select trace thread");

        Throwable readEventsFailure = catchThrowable(() -> readEvents.invoke(null, tempDir));
        assertThat(readEventsFailure).isInstanceOf(InvocationTargetException.class);
        assertThat(readEventsFailure.getCause())
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("Failed reading trace file");
    }

    @Test
    void privateJsonScannerHelpersCoverWhitespacePrimitiveArraysAndMalformedBranches() throws Exception {
        Method parseObjectFields = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("parseObjectFields", String.class);
        parseObjectFields.setAccessible(true);
        Method parseNestedObjectFields = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod(
            "parseNestedObjectFields",
            String.class,
            String.class
        );
        parseNestedObjectFields.setAccessible(true);
        Method skipJsonValue = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("skipJsonValue", String.class, int.class);
        skipJsonValue.setAccessible(true);
        Method skipJsonStructure = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod(
            "skipJsonStructure",
            String.class,
            int.class,
            char.class,
            char.class
        );
        skipJsonStructure.setAccessible(true);
        Method parseJsonString = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("parseJsonString", String.class, int.class);
        parseJsonString.setAccessible(true);
        Method unescapeJsonChar = GenerateActivityPumlFromTraceTask.class.getDeclaredMethod("unescapeJsonChar", char.class);
        unescapeJsonChar.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> parsedObject = (Map<String, String>) parseObjectFields.invoke(
            null,
            "{  \"name\" : \"ok\" , \"flag\" : true , \"list\" : [\"x\"] , \"nested\" : {\"skip\":\"me\"} , \"tail\" : \"done\" }"
        );
        assertThat(parsedObject)
            .containsEntry("name", "ok")
            .containsEntry("tail", "done")
            .doesNotContainKeys("flag", "list", "nested");
        assertThat(parseObjectFields.invoke(null, "plain")).isEqualTo(Map.of());
        assertThat(parseObjectFields.invoke(null, "{\"broken\" \"value\"}")).isEqualTo(Map.of());
        assertThat(parseObjectFields.invoke(null, "{\"broken\":\"unterminated}")).isEqualTo(Map.of());
        assertThat(parseObjectFields.invoke(null, "{\"broken\":   ")).isEqualTo(Map.of());

        @SuppressWarnings("unchecked")
        Map<String, String> nestedObject = (Map<String, String>) parseNestedObjectFields.invoke(
            null,
            "{ \"details\" : { \"line\" : \"a\\r\\tb\\q\" }, \"event\" : \"METHOD_EXIT\" }",
            "details"
        );
        assertThat(nestedObject).containsEntry("line", "a\r\tbq");
        assertThat(parseNestedObjectFields.invoke(null, "plain", "details")).isEqualTo(Map.of());
        assertThat(parseNestedObjectFields.invoke(null, "{ \"details\" { \"broken\" : \"value\" } }", "details"))
            .isEqualTo(Map.of());
        assertThat(parseNestedObjectFields.invoke(null, "{ \"details\" :   ", "details")).isEqualTo(Map.of());

        String primitiveSource = "{\"flag\":true}";
        assertThat(skipJsonValue.invoke(null, primitiveSource, primitiveSource.indexOf("true")))
            .isEqualTo(primitiveSource.indexOf('}'));
        String primitiveTailSource = "{\"flag\":true,\"tail\":\"ok\"}";
        assertThat(skipJsonValue.invoke(null, primitiveTailSource, primitiveTailSource.indexOf("true")))
            .isEqualTo(primitiveTailSource.indexOf(','));
        String arraySource = "{\"list\":[\"x\"]}";
        assertThat(skipJsonValue.invoke(null, arraySource, arraySource.indexOf('[')))
            .isEqualTo(arraySource.length() - 1);
        String invalidStringSource = "\"unterminated";
        assertThat(skipJsonValue.invoke(null, invalidStringSource, 0)).isEqualTo(invalidStringSource.length());
        String unterminatedObject = "{\"a\":\"b\"";
        assertThat(skipJsonStructure.invoke(null, unterminatedObject, 0, '{', '}')).isEqualTo(unterminatedObject.length());

        assertThat(parseJsonString.invoke(null, "value", 0)).isNull();
        assertThat(parseJsonString.invoke(null, "\"unterminated", 0)).isNull();
        assertThat(unescapeJsonChar.invoke(null, 'r')).isEqualTo('\r');
        assertThat(unescapeJsonChar.invoke(null, 't')).isEqualTo('\t');
        assertThat(unescapeJsonChar.invoke(null, 'q')).isEqualTo('q');
    }
}
