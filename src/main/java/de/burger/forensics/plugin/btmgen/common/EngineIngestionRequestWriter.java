package de.burger.forensics.plugin.btmgen.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class EngineIngestionRequestWriter {

    public void write(Path target, EngineIngestionRequest request) {
        try {
            Path normalizedTarget = target.toAbsolutePath().normalize();
            Path parent = normalizedTarget.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(normalizedTarget, toJson(request));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write engine ingestion request " + target, exception);
        }
    }

    String toJson(EngineIngestionRequest request) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendField(json, 1, "schemaVersion", request.schemaVersion(), true);
        appendObjectStart(json, 1, "buildIdentity");
        appendField(json, 2, "projectId", request.projectId(), true);
        appendField(json, 2, "repositoryUrl", request.repositoryUrl(), true);
        appendField(json, 2, "branchName", request.branchName(), true);
        appendField(json, 2, "commitHash", request.commitHash(), true);
        appendField(json, 2, "buildId", request.buildId(), true);
        appendField(json, 2, "scanTimestamp", DateTimeFormatter.ISO_INSTANT.format(request.scanTimestamp()), false);
        appendObjectEnd(json, 1, true);
        appendObjectStart(json, 1, "moduleIdentity");
        appendField(json, 2, "moduleName", request.moduleName(), true);
        appendField(json, 2, "modulePath", request.modulePath(), false);
        appendObjectEnd(json, 1, true);
        appendObjectStart(json, 1, "pluginIdentity");
        appendField(json, 2, "pluginName", request.pluginName(), true);
        appendField(json, 2, "pluginVersion", request.pluginVersion(), false);
        appendObjectEnd(json, 1, true);
        appendPayloads(json, request);
        json.append("}\n");
        return json.toString();
    }

    private static void appendPayloads(StringBuilder json, EngineIngestionRequest request) {
        indent(json, 1).append("\"payloads\": [\n");
        for (int index = 0; index < request.payloads().size(); index++) {
            EngineIngestionPayload payload = request.payloads().get(index);
            indent(json, 2).append("{\n");
            appendField(json, 3, "payloadId", payload.payloadId(), true);
            appendField(json, 3, "kind", payload.kind().name(), true);
            appendField(json, 3, "contentType", payload.contentType(), true);
            appendField(json, 3, "file", normalizePath(payload.file()), true);
            appendAttributes(json, payload.attributes());
            indent(json, 2).append("}");
            json.append(index + 1 == request.payloads().size() ? "\n" : ",\n");
        }
        indent(json, 1).append("]\n");
    }

    private static void appendAttributes(StringBuilder json, Map<String, String> attributes) {
        indent(json, 3).append("\"attributes\": {");
        if (attributes.isEmpty()) {
            json.append("}\n");
            return;
        }
        json.append("\n");
        int index = 0;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            appendField(json, 4, entry.getKey(), entry.getValue(), index + 1 < attributes.size());
            index++;
        }
        indent(json, 3).append("}\n");
    }

    private static void appendObjectStart(StringBuilder json, int level, String name) {
        indent(json, level).append('"').append(name).append("\": {\n");
    }

    private static void appendObjectEnd(StringBuilder json, int level, boolean comma) {
        indent(json, level).append('}').append(comma ? "," : "").append('\n');
    }

    private static void appendField(StringBuilder json, int level, String name, String value, boolean comma) {
        indent(json, level)
                .append('"').append(escape(name)).append("\": ")
                .append('"').append(escape(value)).append('"')
                .append(comma ? "," : "")
                .append('\n');
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }

    private static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }
}
