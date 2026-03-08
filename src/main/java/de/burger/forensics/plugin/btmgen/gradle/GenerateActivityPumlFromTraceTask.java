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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GenerateActivityPumlFromTraceTask extends DefaultTask {
    private static final Pattern FIELD_PATTERN = Pattern.compile("\"([^\"]+)\":\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern DETAILS_PATTERN = Pattern.compile("\"details\"\\s*:\\s*\\{(.*)}\\s*$");

    @InputFile
    public abstract RegularFileProperty getInputTrace();

    @OutputFile
    public abstract RegularFileProperty getOutputPuml();

    @Input
    @Optional
    public abstract Property<@NotNull String> getRootClass();

    @Input
    @Optional
    public abstract Property<@NotNull String> getRootMethod();

    @TaskAction
    public void generate() {
        Path input = getInputTrace().get().getAsFile().toPath();
        if (!Files.exists(input)) {
            throw new GradleException("Trace input not found: " + input.toAbsolutePath());
        }

        List<TraceEvent> allEvents = readEvents(input);
        if (allEvents.isEmpty()) {
            throw new GradleException("No trace events found in: " + input.toAbsolutePath());
        }

        String selectedThread = selectThread(allEvents);
        List<TraceEvent> events = allEvents.stream()
                .filter(e -> selectedThread.equals(e.thread()))
                .toList();
        if (events.isEmpty()) {
            throw new GradleException("No events for selected thread: " + selectedThread);
        }

        MethodRef endpoint = selectEndpoint(events);
        int endpointIndex = findLastMethodExitIndex(events, endpoint);
        if (endpointIndex < 0) {
            throw new GradleException("No METHOD_EXIT endpoint found for " + endpoint.className() + "#" + endpoint.methodName());
        }
        int previousEndpointIndex = findPreviousMethodExitIndex(events, endpoint, endpointIndex);
        int start = previousEndpointIndex >= 0 ? previousEndpointIndex + 1 : 0;
        List<TraceEvent> path = events.subList(start, endpointIndex + 1);
        if (path.isEmpty()) {
            throw new GradleException("Selected path is empty.");
        }

        Instant t0 = path.get(0).timestamp();
        StringBuilder sb = new StringBuilder();
        String firstLane = simpleName(endpoint.className());
        sb.append("@startuml\n");
        sb.append("|").append(firstLane).append("|\n");
        sb.append("start\n\n");

        String pendingConditionExpr = null;
        String pendingConditionValue = null;
        for (TraceEvent event : path) {
            if ("BRANCH_TAKEN".equals(event.event())) {
                String kind = event.details().get("kind");
                if ("if".equalsIgnoreCase(kind)) {
                    String cls = event.details().getOrDefault("class", endpoint.className());
                    String method = event.details().getOrDefault("method", "unknown");
                    String branch = event.details().getOrDefault("branch", "UNKNOWN");
                    String conditionText = pendingConditionExpr != null ? pendingConditionExpr : cls + "#" + method;
                    appendIfBlock(
                            sb,
                            simpleName(cls),
                            conditionText,
                            branch,
                            pendingConditionValue,
                            elapsedMs(t0, event.timestamp()),
                            event.tsRaw()
                    );
                    pendingConditionExpr = null;
                    pendingConditionValue = null;
                } else {
                    String label = event.details().get("label");
                    String value = event.details().get("value");
                    if (label != null) {
                        pendingConditionExpr = normalizeLabel(label);
                        pendingConditionValue = value;
                    }
                }
                continue;
            }

            if ("METHOD_EXIT".equals(event.event())) {
                String cls = event.details().get("class");
                String method = event.details().get("method");
                if (cls == null || method == null) {
                    continue;
                }
                String result = event.details().get("result");
                sb.append("|").append(simpleName(cls)).append("|\n");
                if (result != null && !result.isBlank()) {
                    sb.append(":").append(escape(method)).append("() -> ").append(escape(result)).append(";\n");
                } else {
                    sb.append(":").append(escape(method)).append("();\n");
                }
                appendNote(sb, elapsedMs(t0, event.timestamp()), event.tsRaw());
                sb.append("\n");
            }
        }

        sb.append("|").append(firstLane).append("|\n");
        sb.append("note right\n");
        sb.append("Trace limits:\\n");
        sb.append("- visible events are METHOD_EXIT and BRANCH_TAKEN\\n");
        sb.append("- exact input parameters are not observable\\n");
        sb.append("- times are event offsets from t0, not self-time\\n");
        sb.append("- internal calls may exist but are not visible\n");
        sb.append("end note\n");
        sb.append("stop\n");
        sb.append("@enduml\n");

        Path out = getOutputPuml().get().getAsFile().toPath();
        try {
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString());
        } catch (IOException e) {
            throw new GradleException("Failed writing PUML output: " + out.toAbsolutePath(), e);
        }

        getLogger().lifecycle("Generated trace activity PUML -> {}", out.toAbsolutePath());
    }

    private static void appendIfBlock(
            StringBuilder sb,
            String lane,
            String conditionText,
            String branch,
            String observedValue,
            double ms,
            String tsRaw
    ) {
        boolean trueTaken = "IF_TRUE".equalsIgnoreCase(branch);
        String observedSuffix = observedValue != null && !observedValue.isBlank()
                ? " (observed=" + escape(observedValue) + ")"
                : "";
        sb.append("|").append(lane).append("|\n");
        sb.append("if (").append(escape(conditionText)).append(") then (true)\n");
        if (trueTaken) {
            sb.append(":IF_TRUE taken").append(observedSuffix).append(";\n");
            appendNote(sb, ms, tsRaw);
            sb.append("else (false)\n");
            sb.append(":not taken;\n");
        } else {
            sb.append(":not taken;\n");
            sb.append("else (false)\n");
            sb.append(":IF_FALSE taken").append(observedSuffix).append(";\n");
            appendNote(sb, ms, tsRaw);
        }
        sb.append("endif\n\n");
    }

    private static void appendNote(StringBuilder sb, double ms, String tsRaw) {
        sb.append("note right\n");
        sb.append(String.format(java.util.Locale.US, "%.1f ms from t0%n", ms));
        sb.append("@ ").append(tsRaw).append("\n");
        sb.append("end note\n");
    }

    private MethodRef selectEndpoint(List<TraceEvent> events) {
        String rc = getRootClass().getOrNull();
        String rm = getRootMethod().getOrNull();
        if (rc != null && rm != null && !rc.isBlank() && !rm.isBlank()) {
            return new MethodRef(rc.trim(), rm.trim());
        }

        List<TraceEvent> exits = events.stream()
                .filter(e -> "METHOD_EXIT".equals(e.event()))
                .toList();
        if (exits.isEmpty()) {
            throw new GradleException("Trace does not contain METHOD_EXIT events.");
        }

        TraceEvent preferred = exits.stream()
                .filter(e -> {
                    String cls = e.details().get("class");
                    return cls != null && cls.contains("Service");
                })
                .max(Comparator.comparing(TraceEvent::timestamp))
                .orElseGet(() -> exits.get(exits.size() - 1));

        return new MethodRef(preferred.details().get("class"), preferred.details().get("method"));
    }

    private static int findLastMethodExitIndex(List<TraceEvent> events, MethodRef endpoint) {
        for (int i = events.size() - 1; i >= 0; i--) {
            TraceEvent e = events.get(i);
            if ("METHOD_EXIT".equals(e.event())
                    && endpoint.className().equals(e.details().get("class"))
                    && endpoint.methodName().equals(e.details().get("method"))) {
                return i;
            }
        }
        return -1;
    }

    private static int findPreviousMethodExitIndex(List<TraceEvent> events, MethodRef endpoint, int before) {
        for (int i = before - 1; i >= 0; i--) {
            TraceEvent e = events.get(i);
            if ("METHOD_EXIT".equals(e.event())
                    && endpoint.className().equals(e.details().get("class"))
                    && endpoint.methodName().equals(e.details().get("method"))) {
                return i;
            }
        }
        return -1;
    }

    private static String selectThread(List<TraceEvent> events) {
        Map<String, Integer> counts = new HashMap<>();
        events.forEach(e -> counts.merge(e.thread(), 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new GradleException("Could not select trace thread."));
    }

    private static List<TraceEvent> readEvents(Path input) {
        try {
            List<TraceEvent> out = new ArrayList<>();
            for (String line : Files.readAllLines(input)) {
                if (line.isBlank()) continue;
                parseLine(line).ifPresent(out::add);
            }
            out.sort(Comparator.comparing(TraceEvent::timestamp));
            return out;
        } catch (IOException e) {
            throw new GradleException("Failed reading trace file " + input.toAbsolutePath(), e);
        }
    }

    private static java.util.Optional<TraceEvent> parseLine(String jsonLine) {
        Map<String, String> root = parseObjectFields(jsonLine);
        String ts = root.get("@ts");
        String event = root.get("event");
        String thread = root.get("thread");
        if (ts == null || event == null || thread == null) {
            return java.util.Optional.empty();
        }
        Matcher detailsMatcher = DETAILS_PATTERN.matcher(jsonLine);
        Map<String, String> details = new LinkedHashMap<>();
        if (detailsMatcher.find()) {
            details = parseObjectFields(detailsMatcher.group(1));
        }
        try {
            return java.util.Optional.of(new TraceEvent(ts, Instant.parse(ts), event, thread, details));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    private static Map<String, String> parseObjectFields(String source) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = FIELD_PATTERN.matcher(source);
        while (m.find()) {
            map.put(m.group(1), unescapeJsonString(m.group(2)));
        }
        return map;
    }

    private static String unescapeJsonString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static double elapsedMs(Instant t0, Instant t) {
        return Duration.between(t0, t).toNanos() / 1_000_000.0;
    }

    private static String normalizeLabel(String label) {
        int idx = label.indexOf(':');
        return idx >= 0 ? label.substring(idx + 1) : label;
    }

    private static String simpleName(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) return "Unknown";
        int idx = fqcn.lastIndexOf('.');
        return idx >= 0 && idx < fqcn.length() - 1 ? fqcn.substring(idx + 1) : fqcn;
    }

    private static String escape(String s) {
        return Objects.toString(s, "")
                .replace("\"", "'")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private record MethodRef(String className, String methodName) { }

    private record TraceEvent(
            String tsRaw,
            Instant timestamp,
            String event,
            String thread,
            Map<String, String> details
    ) { }
}
