package de.burger.forensics.plugin.btmgen.grpc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record ForensicsPayload(
        String payloadId,
        Kind kind,
        String contentType,
        byte[] content,
        Map<String, String> attributes
) {
    public ForensicsPayload {
        payloadId = requireText(payloadId, "payloadId");
        kind = Objects.requireNonNull(kind, "kind");
        contentType = requireText(contentType, "contentType");
        content = Arrays.copyOf(Objects.requireNonNull(content, "content"), content.length);
        if (content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        attributes = sortedAttributes(attributes);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    private static Map<String, String> sortedAttributes(Map<String, String> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        TreeMap<String, String> sorted = new TreeMap<>();
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

    public enum Kind {
        SOURCE_FACTS,
        SEMANTIC_ARTIFACTS,
        RULE_ARTIFACTS,
        RUNTIME_TRACE,
        DIAGNOSTIC_REPORT
    }
}
