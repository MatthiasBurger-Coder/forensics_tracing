package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GenerateActivityPumlFromBtmTask extends DefaultTask {
    private static final Pattern EVAL_PATTERN = Pattern.compile("^\\s*IF\\s+eval\\(\".*?\",\\s*\"(.*?)\"\\s*,.*$");

    @InputFile
    public abstract RegularFileProperty getInputBtm();

    @OutputFile
    public abstract RegularFileProperty getOutputPuml();

    @Input
    @Optional
    public abstract Property<@NotNull String> getDiagramTitle();

    @TaskAction
    public void generate() {
        Path inputPath = getInputBtm().get().getAsFile().toPath();
        if (!Files.exists(inputPath)) {
            throw new GradleException("BTM input not found: " + inputPath.toAbsolutePath());
        }

        final String content;
        try {
            content = Files.readString(inputPath);
        } catch (IOException e) {
            throw new GradleException("Failed to read BTM input: " + inputPath.toAbsolutePath(), e);
        }

        List<ParsedRule> rules = parseRules(content);
        if (rules.isEmpty()) {
            throw new GradleException("No parsable rules found in " + inputPath.toAbsolutePath());
        }

        Map<String, Map<String, MethodSummary>> classToMethods = new LinkedHashMap<>();
        rules.forEach(rule -> {
            Map<String, MethodSummary> methods = classToMethods.computeIfAbsent(rule.className(), k -> new LinkedHashMap<>());
            MethodSummary summary = methods.computeIfAbsent(rule.methodName(), MethodSummary::new);
            switch (rule.eventType()) {
                case METHOD_ENTER -> summary.enterCount++;
                case METHOD_EXIT -> summary.exitCount++;
                case IF_TRUE -> summary.ifTrueCount++;
                case IF_FALSE -> summary.ifFalseCount++;
                case SWITCH -> summary.switchCount++;
                case SWITCH_CASE -> summary.switchCaseCount++;
                case RETURN -> summary.returnCount++;
                case THROW -> summary.throwCount++;
                case OTHER -> { }
            }
            if (rule.condition() != null && !rule.condition().isBlank()) {
                summary.conditions.add(rule.condition());
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title ").append(sanitize(getDiagramTitle().getOrElse("Forensics Activity Diagram"))).append("\n");
        sb.append("skinparam shadowing false\n");
        sb.append("start\n");

        classToMethods.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(classEntry -> {
                    sb.append("partition \"").append(sanitize(classEntry.getKey())).append("\" {\n");
                    classEntry.getValue().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(Map.Entry::getValue)
                            .forEach(method -> writeMethodBlock(sb, method));
                    sb.append("}\n");
                });

        sb.append("stop\n");
        sb.append("@enduml\n");

        Path outputPath = getOutputPuml().get().getAsFile().toPath();
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, sb.toString());
        } catch (IOException e) {
            throw new GradleException("Failed to write PUML output: " + outputPath.toAbsolutePath(), e);
        }

        getLogger().lifecycle("Generated activity PUML with swimlanes -> {}", outputPath.toAbsolutePath());
    }

    private static void writeMethodBlock(StringBuilder sb, MethodSummary method) {
        sb.append(":").append(sanitize(method.methodName)).append("();\n");
        if (method.enterCount > 0) sb.append(":METHOD_ENTER x").append(method.enterCount).append(";\n");
        if (method.ifTrueCount > 0 || method.ifFalseCount > 0) {
            String condition = method.conditions.stream().findFirst().orElse("condition");
            sb.append("if (").append(sanitize(condition)).append(") then (true)\n");
            sb.append(":IF_TRUE x").append(method.ifTrueCount).append(";\n");
            sb.append("else (false)\n");
            sb.append(":IF_FALSE x").append(method.ifFalseCount).append(";\n");
            sb.append("endif\n");
        }
        if (method.switchCount > 0) sb.append(":SWITCH x").append(method.switchCount).append(";\n");
        if (method.switchCaseCount > 0) sb.append(":SWITCH_CASE x").append(method.switchCaseCount).append(";\n");
        if (method.throwCount > 0) sb.append(":THROW x").append(method.throwCount).append(";\n");
        if (method.returnCount > 0) sb.append(":RETURN x").append(method.returnCount).append(";\n");
        if (method.exitCount > 0) sb.append(":METHOD_EXIT x").append(method.exitCount).append(";\n");
    }

    private static List<ParsedRule> parseRules(String content) {
        String[] blocks = content.split("(?m)^\\s*ENDRULE\\s*$");
        List<ParsedRule> rules = new ArrayList<>();
        for (String block : blocks) {
            List<String> lines = block.lines()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            if (lines.stream().noneMatch(s -> s.startsWith("RULE "))) {
                continue;
            }
            String className = firstValue(lines, "CLASS ");
            String methodName = firstValue(lines, "METHOD ");
            if (className == null || methodName == null) {
                continue;
            }
            String ifLine = lines.stream().filter(s -> s.startsWith("IF ")).findFirst().orElse(null);
            String condition = extractCondition(ifLine);
            String doLine = lines.stream().filter(s -> s.startsWith("on") || s.startsWith("DO on")).findFirst().orElse("");
            rules.add(new ParsedRule(className, methodName, detectEvent(doLine), condition));
        }
        return rules;
    }

    private static String extractCondition(String ifLine) {
        if (ifLine == null) return null;
        if ("IF true".equals(ifLine) || "IF false".equals(ifLine)) {
            return ifLine.substring(3).trim();
        }
        Matcher m = EVAL_PATTERN.matcher(ifLine);
        if (m.matches()) {
            return m.group(1);
        }
        return ifLine.substring(3).trim();
    }

    private static EventType detectEvent(String doLine) {
        if (doLine.contains("onEnter(")) return EventType.METHOD_ENTER;
        if (doLine.contains("onExit(")) return EventType.METHOD_EXIT;
        if (doLine.contains("onSwitch(")) return EventType.SWITCH;
        if (doLine.contains("onCase(")) return EventType.SWITCH_CASE;
        if (doLine.contains("onException(")) return EventType.THROW;
        if (doLine.contains("onReturn(")) return EventType.RETURN;
        if (doLine.contains("onBranch(") && doLine.contains("\"IF_TRUE\"")) return EventType.IF_TRUE;
        if (doLine.contains("onBranch(") && doLine.contains("\"IF_FALSE\"")) return EventType.IF_FALSE;
        return EventType.OTHER;
    }

    private static String firstValue(List<String> lines, String prefix) {
        return lines.stream()
                .filter(s -> s.startsWith(prefix))
                .findFirst()
                .map(s -> s.substring(prefix.length()).trim())
                .orElse(null);
    }

    private static String sanitize(String value) {
        return value
                .replace("\"", "'")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    private record ParsedRule(String className, String methodName, EventType eventType, String condition) { }

    private enum EventType {
        METHOD_ENTER,
        METHOD_EXIT,
        IF_TRUE,
        IF_FALSE,
        SWITCH,
        SWITCH_CASE,
        RETURN,
        THROW,
        OTHER
    }

    private static final class MethodSummary {
        private final String methodName;
        private int enterCount;
        private int exitCount;
        private int returnCount;
        private int throwCount;
        private int switchCount;
        private int switchCaseCount;
        private int ifTrueCount;
        private int ifFalseCount;
        private final LinkedHashSet<String> conditions = new LinkedHashSet<>();

        private MethodSummary(String methodName) {
            this.methodName = methodName;
        }
    }
}
