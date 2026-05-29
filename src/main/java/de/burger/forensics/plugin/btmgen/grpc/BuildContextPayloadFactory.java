package de.burger.forensics.plugin.btmgen.grpc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class BuildContextPayloadFactory {
    private BuildContextPayloadFactory() {
    }

    public static ForensicsPayload create(String moduleName, String modulePath, String projectId) {
        return new ForensicsPayload(
                "build-context",
                ForensicsPayload.Kind.DIAGNOSTIC_REPORT,
                "application/json",
                json(moduleName, modulePath, projectId).getBytes(StandardCharsets.UTF_8),
                Map.of("artifact", "build-context")
        );
    }

    private static String json(String moduleName, String modulePath, String projectId) {
        return """
                {
                  "moduleName": "%s",
                  "modulePath": "%s",
                  "projectId": "%s"
                }
                """.formatted(escape(moduleName), escape(modulePath), escape(projectId));
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
}
