package de.burger.forensics.plugin.adapters;

import de.burger.forensics.domain.model.cache.ScanPhase;
import de.burger.forensics.domain.model.cache.ScanProfile;
import de.burger.forensics.domain.port.out.ScanProfileSinkPort;
import de.burger.forensics.domain.validation.ConditionValidationIssue;
import de.burger.forensics.domain.validation.ConditionValidationReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Writes scan profiling data as a deterministic JSON document.
 */
public final class JsonScanProfileSinkAdapter implements ScanProfileSinkPort {

    private final Path reportFile;

    public JsonScanProfileSinkAdapter(Path reportFile) {
        this.reportFile = Objects.requireNonNull(reportFile, "Report file must not be null.");
    }

    @Override
    public void publish(ScanProfile profile) {
        publish(profile, ConditionValidationReport.empty());
    }

    public void publish(ScanProfile profile, ConditionValidationReport validationReport) {
        Objects.requireNonNull(profile, "Scan profile must not be null.");
        Objects.requireNonNull(validationReport, "Validation report must not be null.");
        try {
            Path parent = reportFile.toAbsolutePath().normalize().getParent();
            Files.createDirectories(parent);
            Files.writeString(reportFile, toJson(profile, validationReport));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write scan profile report " + reportFile + ".", exception);
        }
    }

    private String toJson(ScanProfile profile, ConditionValidationReport validationReport) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendCounter(json, "totalFiles", profile.totalFiles());
        appendCounter(json, "parsedFiles", profile.parsedFiles());
        appendCounter(json, "cacheHitFiles", profile.cacheHitFiles());
        appendCounter(json, "cacheMissFiles", profile.cacheMissFiles());
        appendCounter(json, "failedFiles", profile.failedFiles());
        appendCounter(json, "totalMethods", profile.totalMethods());
        appendCounter(json, "totalEvents", profile.totalEvents());
        appendCounter(json, "totalDependencies", profile.totalDependencies());
        json.append("  \"phaseDurationsNanos\": {");
        appendDurations(json, profile.phaseDurations());
        json.append("\n  },\n");
        appendValidationReport(json, validationReport);
        json.append("}\n");
        return json.toString();
    }

    private void appendCounter(StringBuilder json, String name, int value) {
        json.append("  \"")
                .append(escape(name))
                .append("\": ")
                .append(value)
                .append(",\n");
    }

    private void appendDurations(StringBuilder json, Map<ScanPhase, Duration> durations) {
        if (durations.isEmpty()) {
            return;
        }
        json.append('\n');
        String[] entries = durations.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> "    \"" + escape(entry.getKey().name()) + "\": " + entry.getValue().toNanos())
                .toArray(String[]::new);
        json.append(String.join(",\n", entries));
    }

    private void appendValidationReport(StringBuilder json, ConditionValidationReport validationReport) {
        json.append("  \"conditionValidation\": {\n");
        json.append("    \"issueCount\": ").append(validationReport.issueCount()).append(",\n");
        json.append("    \"uniqueSymbolCount\": ").append(validationReport.uniqueSymbolCount()).append(",\n");
        json.append("    \"issues\": [");
        if (!validationReport.issues().isEmpty()) {
            json.append('\n');
            String[] issues = validationReport.issues().stream()
                    .map(this::validationIssueJson)
                    .toArray(String[]::new);
            json.append(String.join(",\n", issues));
            json.append('\n');
            json.append("    ");
        }
        json.append("]\n");
        json.append("  }\n");
    }

    private String validationIssueJson(ConditionValidationIssue issue) {
        String template = issue.template() == null ? "" : issue.template().name();
        return "      {"
                + "\"symbol\":\"" + escape(issue.symbol()) + "\","
                + "\"className\":\"" + escape(issue.location().fqcn()) + "\","
                + "\"methodName\":\"" + escape(issue.location().method()) + "\","
                + "\"line\":" + issue.location().line() + ","
                + "\"template\":\"" + escape(template) + "\","
                + "\"expressionPreview\":\"" + escape(issue.expressionPreview()) + "\""
                + "}";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
