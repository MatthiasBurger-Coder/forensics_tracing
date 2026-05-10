package de.burger.forensics.plugin.btmgen.common;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record EngineIngestionPayload(
        String payloadId,
        EnginePayloadKind kind,
        String contentType,
        Path file,
        Map<String, String> attributes
) {
    public EngineIngestionPayload {
        payloadId = requireText(payloadId, "payloadId");
        kind = Objects.requireNonNull(kind, "kind");
        contentType = requireText(contentType, "contentType");
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        attributes = sortedAttributes(attributes);
    }

    private static Map<String, String> sortedAttributes(Map<String, String> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        var sorted = new TreeMap<String, String>();
        attributes.forEach((key, value) -> sorted.put(
                requireText(key, "attribute key"),
                requireText(value, "attribute value")
        ));
        return Collections.unmodifiableMap(sorted);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
