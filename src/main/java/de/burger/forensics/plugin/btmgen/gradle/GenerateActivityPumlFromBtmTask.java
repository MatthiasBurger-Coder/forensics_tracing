package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@DisableCachingByDefault(because = "Activity diagrams are generated reports and are cheap to recreate.")
public abstract class GenerateActivityPumlFromBtmTask extends DefaultTask {
    private static final int MAX_DETAILED_SWIMLANE_CLASSES = 64;
    private static final int MAX_DETAILED_METHOD_BLOCKS = 400;
    private static final int MAX_COMPACT_SUMMARY_CLASSES_PER_PAGE = 75;
    private static final String SUMMARY_LANE = "ActivitySummary";

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
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
                case OTHER -> {
                    // Ignore unsupported rule actions in the static activity summary.
                }
            }
            if (rule.condition() != null && !rule.condition().isBlank()) {
                summary.conditions.add(rule.condition());
            }
        });

        List<Map.Entry<String, Map<String, MethodSummary>>> sortedClasses = classToMethods.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        int totalMethodBlocks = sortedClasses.stream()
                .map(Map.Entry::getValue)
                .mapToInt(Map::size)
                .sum();
        boolean useCompactSummary = requiresCompactSummary(sortedClasses.size(), totalMethodBlocks);

        String title = sanitize(getDiagramTitle().getOrElse("Forensics Activity Diagram"));
        StringBuilder sb = new StringBuilder();
        if (useCompactSummary) {
            writeCompactSummaryDiagrams(sb, title, sortedClasses, totalMethodBlocks);
        } else {
            writeDetailedDiagram(sb, title, sortedClasses);
        }

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

        getLogger().lifecycle(
                "Generated activity PUML ({}) -> {}",
                useCompactSummary ? "compact summary" : "detailed swimlanes",
                outputPath.toAbsolutePath()
        );
    }

    private static boolean requiresCompactSummary(int classCount, int totalMethodBlocks) {
        return classCount > MAX_DETAILED_SWIMLANE_CLASSES || totalMethodBlocks > MAX_DETAILED_METHOD_BLOCKS;
    }

    private static void writeDetailedDiagram(
            StringBuilder sb,
            String title,
            List<Map.Entry<String, Map<String, MethodSummary>>> sortedClasses
    ) {
        sb.append("@startuml\n");
        sb.append("title ").append(title).append("\n");
        sb.append("skinparam shadowing false\n");
        String firstLane = simpleName(sortedClasses.get(0).getKey());
        sb.append("|").append(firstLane).append("|\n");
        sb.append("start\n\n");

        sortedClasses.forEach(classEntry -> classEntry.getValue().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .forEach(method -> writeMethodBlock(sb, simpleName(classEntry.getKey()), method)));

        sb.append("|").append(firstLane).append("|\n");
        sb.append("note right\n");
        sb.append("BTM limits:\\n");
        sb.append("- this view comes from static Byteman rules, not runtime events\\n");
        sb.append("- exact executed branch and timings are not available\\n");
        sb.append("- counts show available instrumentation rules per method\n");
        sb.append("end note\n");
        sb.append("stop\n");
        sb.append("@enduml\n");
    }

    private static void writeCompactSummaryDiagrams(
            StringBuilder sb,
            String title,
            List<Map.Entry<String, Map<String, MethodSummary>>> sortedClasses,
            int totalMethodBlocks
    ) {
        int totalPages = Math.max(1, (int) Math.ceil((double) sortedClasses.size() / MAX_COMPACT_SUMMARY_CLASSES_PER_PAGE));
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            if (pageIndex > 0) {
                sb.append("\n");
            }
            int startIndex = pageIndex * MAX_COMPACT_SUMMARY_CLASSES_PER_PAGE;
            int endIndex = Math.min(sortedClasses.size(), startIndex + MAX_COMPACT_SUMMARY_CLASSES_PER_PAGE);
            List<Map.Entry<String, Map<String, MethodSummary>>> page = sortedClasses.subList(startIndex, endIndex);

            sb.append("@startuml\n");
            sb.append("title ").append(titleForPage(title, pageIndex + 1, totalPages)).append("\n");
            sb.append("skinparam shadowing false\n");
            sb.append("|").append(SUMMARY_LANE).append("|\n");
            sb.append("start\n\n");

            page.forEach(classEntry -> sb.append(":")
                    .append(sanitize(formatCompactSummary(simpleName(classEntry.getKey()), classEntry.getValue())))
                    .append(";\n"));

            sb.append("|").append(SUMMARY_LANE).append("|\n");
            sb.append("note right\n");
            sb.append("BTM limits:\\n");
            sb.append("- this view comes from static Byteman rules, not runtime events\\n");
            sb.append("- exact executed branch and timings are not available\\n");
            sb.append("- compact summary mode enabled for ")
                    .append(sortedClasses.size())
                    .append(" classes / ")
                    .append(totalMethodBlocks)
                    .append(" methods\\n");
            sb.append("- page ")
                    .append(pageIndex + 1)
                    .append("/")
                    .append(totalPages)
                    .append(" shows classes ")
                    .append(startIndex + 1)
                    .append("-")
                    .append(endIndex)
                    .append("\\n");
            sb.append("- detailed per-method swimlanes are skipped to keep the diagram readable\n");
            sb.append("end note\n");
            sb.append("stop\n");
            sb.append("@enduml\n");
        }
    }

    private static String titleForPage(String title, int pageNumber, int totalPages) {
        if (totalPages <= 1) {
            return title;
        }
        return title + " (page " + pageNumber + "/" + totalPages + ")";
    }

    private static String formatCompactSummary(String className, Map<String, MethodSummary> methods) {
        ClassTotals totals = methods.values().stream()
                .reduce(
                        new ClassTotals(methods.size(), 0, 0, 0, 0, 0, 0, 0),
                        (current, method) -> new ClassTotals(
                                current.methodCount(),
                                current.enterCount() + method.enterCount,
                                current.exitCount() + method.exitCount,
                                current.returnCount() + method.returnCount,
                                current.throwCount() + method.throwCount,
                                current.switchCount() + method.switchCount,
                                current.switchCaseCount() + method.switchCaseCount,
                                current.branchCount() + method.ifTrueCount + method.ifFalseCount
                        ),
                        (left, right) -> new ClassTotals(
                                left.methodCount() + right.methodCount(),
                                left.enterCount() + right.enterCount(),
                                left.exitCount() + right.exitCount(),
                                left.returnCount() + right.returnCount(),
                                left.throwCount() + right.throwCount(),
                                left.switchCount() + right.switchCount(),
                                left.switchCaseCount() + right.switchCaseCount(),
                                left.branchCount() + right.branchCount()
                        )
                );

        StringBuilder summary = new StringBuilder(className)
                .append(" [methods=").append(totals.methodCount())
                .append(", enter=").append(totals.enterCount())
                .append(", exit=").append(totals.exitCount());
        appendNonZeroSummary(summary, "branches", totals.branchCount());
        appendNonZeroSummary(summary, "return", totals.returnCount());
        appendNonZeroSummary(summary, "throw", totals.throwCount());
        appendNonZeroSummary(summary, "switch", totals.switchCount());
        appendNonZeroSummary(summary, "cases", totals.switchCaseCount());
        return summary.append("]").toString();
    }

    private static void appendNonZeroSummary(StringBuilder summary, String label, int value) {
        if (value > 0) {
            summary.append(", ").append(label).append("=").append(value);
        }
    }

    private static void writeMethodBlock(StringBuilder sb, String lane, MethodSummary method) {
        sb.append("|").append(lane).append("|\n");
        sb.append(":").append(sanitize(method.methodName)).append("();\n");
        if (method.enterCount > 0) {
            sb.append("|").append(lane).append("|\n");
            sb.append(":METHOD_ENTER x").append(method.enterCount).append(";\n");
        }
        if (method.ifTrueCount > 0 || method.ifFalseCount > 0) {
            String condition = method.conditions.stream().findFirst().orElse("condition");
            sb.append("|").append(lane).append("|\n");
            sb.append("if (").append(sanitize(condition)).append(") then (true)\n");
            sb.append(":IF_TRUE rule x").append(method.ifTrueCount).append(";\n");
            sb.append("else (false)\n");
            sb.append(":IF_FALSE rule x").append(method.ifFalseCount).append(";\n");
            sb.append("endif\n");
        }
        if (method.switchCount > 0) {
            sb.append("|").append(lane).append("|\n");
            sb.append(":SWITCH rule x").append(method.switchCount).append(";\n");
        }
        if (method.switchCaseCount > 0) {
            sb.append("|").append(lane).append("|\n");
            sb.append(":SWITCH_CASE rule x").append(method.switchCaseCount).append(";\n");
        }
        if (method.throwCount > 0) {
            sb.append("|").append(lane).append("|\n");
            sb.append(":THROW rule x").append(method.throwCount).append(";\n");
        }
        if (method.returnCount > 0) {
            sb.append("|").append(lane).append("|\n");
            sb.append(":RETURN rule x").append(method.returnCount).append(";\n");
        }
        if (method.exitCount > 0) {
            sb.append("|").append(lane).append("|\n");
            sb.append(":METHOD_EXIT x").append(method.exitCount).append(";\n");
        }
        sb.append("\n");
    }

    private static List<ParsedRule> parseRules(String content) {
        String[] blocks = content.split("(?m)^\\s*ENDRULE\\s*$");
        List<ParsedRule> rules = new ArrayList<>();
        for (String block : blocks) {
            List<String> lines = block.lines()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            boolean hasRuleHeader = lines.stream().anyMatch(s -> s.startsWith("RULE "));
            if (hasRuleHeader) {
                String className = firstValue(lines, "CLASS ");
                String methodName = firstValue(lines, "METHOD ");
                if (className != null && methodName != null) {
                    String ifLine = lines.stream().filter(s -> s.startsWith("IF ")).findFirst().orElse(null);
                    String condition = extractCondition(ifLine);
                    String doLine = lines.stream().filter(s -> s.startsWith("on") || s.startsWith("DO on")).findFirst().orElse("");
                    rules.add(new ParsedRule(className, methodName, detectEvent(doLine), condition));
                }
            }
        }
        return rules;
    }

    private static String extractCondition(String ifLine) {
        if (ifLine == null) return null;
        String trimmed = ifLine.trim();
        if ("IF true".equals(trimmed) || "IF false".equals(trimmed)) {
            return trimmed.substring(3).trim();
        }
        String evalCondition = extractEvalCondition(trimmed);
        if (evalCondition != null) {
            return evalCondition;
        }
        if (trimmed.startsWith("IF ")) {
            return trimmed.substring(3).trim();
        }
        return trimmed;
    }

    private static String extractEvalCondition(String ifLine) {
        if (!ifLine.startsWith("IF")) {
            return null;
        }

        int cursor = skipWhitespace(ifLine, 2);
        if (!ifLine.startsWith("eval(", cursor)) {
            return null;
        }

        cursor = skipWhitespace(ifLine, cursor + "eval(".length());
        ParsedQuotedSegment ruleId = parseQuotedSegment(ifLine, cursor);
        if (ruleId == null) {
            return null;
        }

        cursor = skipWhitespace(ifLine, ruleId.nextIndex());
        if (cursor >= ifLine.length() || ifLine.charAt(cursor) != ',') {
            return null;
        }

        cursor = skipWhitespace(ifLine, cursor + 1);
        ParsedQuotedSegment condition = parseQuotedSegment(ifLine, cursor);
        if (condition == null) {
            return null;
        }

        cursor = skipWhitespace(ifLine, condition.nextIndex());
        if (cursor >= ifLine.length() || ifLine.charAt(cursor) != ',') {
            return null;
        }

        return condition.value();
    }

    private static ParsedQuotedSegment parseQuotedSegment(String source, int quoteIndex) {
        if (quoteIndex >= source.length() || source.charAt(quoteIndex) != '"') {
            return null;
        }

        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int index = quoteIndex + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (escaping) {
                value.append(unescapeQuotedChar(current));
                escaping = false;
            } else if (current == '\\') {
                escaping = true;
            } else if (current == '"') {
                return new ParsedQuotedSegment(value.toString(), index + 1);
            } else {
                value.append(current);
            }
        }
        return null;
    }

    private static int skipWhitespace(String source, int index) {
        int cursor = index;
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static char unescapeQuotedChar(char escaped) {
        return switch (escaped) {
            case '"' -> '"';
            case '\\' -> '\\';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> escaped;
        };
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

    private static String simpleName(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) return "Unknown";
        int idx = fqcn.lastIndexOf('.');
        return idx >= 0 && idx < fqcn.length() - 1 ? fqcn.substring(idx + 1) : fqcn;
    }

    private record ParsedRule(String className, String methodName, EventType eventType, String condition) { }

    private record ParsedQuotedSegment(String value, int nextIndex) { }

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

    private record ClassTotals(
            int methodCount,
            int enterCount,
            int exitCount,
            int returnCount,
            int throwCount,
            int switchCount,
            int switchCaseCount,
            int branchCount
    ) { }
}
