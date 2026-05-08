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

    private static final String JSON_STRING_LINE_SUFFIX = "\",\n";

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
        json.append("    \"suppressedByAllowlist\": ").append(validationReport.suppressedIssueCount()).append(",\n");
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
        json.append("],\n");
        json.append("    \"groups\": [");
        if (!validationReport.symbolGroups().isEmpty()) {
            json.append('\n');
            String[] groups = validationReport.symbolGroups().stream()
                    .map(this::validationGroupJson)
                    .toArray(String[]::new);
            json.append(String.join(",\n", groups));
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

    private String validationGroupJson(ConditionValidationReport.SymbolGroup group) {
        StringBuilder json = new StringBuilder();
        json.append("      {\n");
        json.append("        \"symbol\":\"").append(escape(group.symbol())).append(JSON_STRING_LINE_SUFFIX);
        json.append("        \"totalOccurrences\": ").append(group.occurrenceCount()).append(",\n");
        json.append("        \"packageCount\": ").append(group.packageCount()).append(",\n");
        json.append("        \"classCount\": ").append(group.classCount()).append(",\n");
        json.append("        \"methodCount\": ").append(group.methodCount()).append(",\n");
        json.append("        \"packages\": [");
        if (!group.packages().isEmpty()) {
            json.append('\n');
            String[] packages = group.packages().stream()
                    .map(this::packageGroupJson)
                    .toArray(String[]::new);
            json.append(String.join(",\n", packages));
            json.append('\n');
            json.append("        ");
        }
        json.append("]\n");
        json.append("      }");
        return json.toString();
    }

    private String packageGroupJson(ConditionValidationReport.PackageGroup group) {
        StringBuilder json = new StringBuilder();
        json.append("          {\n");
        json.append("            \"packageName\":\"").append(escape(group.packageName())).append(JSON_STRING_LINE_SUFFIX);
        json.append("            \"classes\": [");
        if (!group.classes().isEmpty()) {
            json.append('\n');
            String[] classes = group.classes().stream()
                    .map(this::classGroupJson)
                    .toArray(String[]::new);
            json.append(String.join(",\n", classes));
            json.append('\n');
            json.append("            ");
        }
        json.append("]\n");
        json.append("          }");
        return json.toString();
    }

    private String classGroupJson(ConditionValidationReport.ClassGroup group) {
        StringBuilder json = new StringBuilder();
        json.append("              {\n");
        json.append("                \"className\":\"").append(escape(group.className())).append(JSON_STRING_LINE_SUFFIX);
        json.append("                \"methods\": [");
        if (!group.methods().isEmpty()) {
            json.append('\n');
            String[] methods = group.methods().stream()
                    .map(this::methodGroupJson)
                    .toArray(String[]::new);
            json.append(String.join(",\n", methods));
            json.append('\n');
            json.append("                ");
        }
        json.append("]\n");
        json.append("              }");
        return json.toString();
    }

    private String methodGroupJson(ConditionValidationReport.MethodGroup group) {
        StringBuilder json = new StringBuilder();
        json.append("                  {\n");
        json.append("                    \"methodName\":\"").append(escape(group.methodName())).append(JSON_STRING_LINE_SUFFIX);
        json.append("                    \"totalOccurrences\": ").append(group.occurrenceCount()).append(",\n");
        json.append("                    \"locations\": [");
        if (!group.issues().isEmpty()) {
            json.append('\n');
            String[] locations = group.issues().stream()
                    .map(this::validationLocationJson)
                    .toArray(String[]::new);
            json.append(String.join(",\n", locations));
            json.append('\n');
            json.append("                    ");
        }
        json.append("]\n");
        json.append("                  }");
        return json.toString();
    }

    private String validationLocationJson(ConditionValidationIssue issue) {
        return "                      {"
                + "\"className\":\"" + escape(locationClassName(issue)) + "\","
                + "\"methodName\":\"" + escape(locationMethodName(issue)) + "\","
                + "\"line\":" + issue.location().line() + ","
                + "\"sourceFilePath\":\"" + escape(issue.sourceContext().sourceFilePath()) + "\","
                + "\"expressionPreview\":\"" + escape(issue.expressionPreview()) + "\""
                + "}";
    }

    private String locationClassName(ConditionValidationIssue issue) {
        String sourceClassName = issue.sourceContext().fullyQualifiedClassName();
        return sourceClassName.isBlank() ? issue.location().fqcn() : sourceClassName;
    }

    private String locationMethodName(ConditionValidationIssue issue) {
        String sourceMethodName = issue.sourceContext().methodName();
        return sourceMethodName.isBlank() ? issue.location().method() : sourceMethodName;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
