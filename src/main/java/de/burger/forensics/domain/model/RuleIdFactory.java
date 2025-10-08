package de.burger.forensics.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds stable rule identifiers based on location and condition text.
 */
public final class RuleIdFactory {
    private RuleIdFactory() {
    }

    public static RuleId from(ScanEvent event, String renderedCondition) {
        Objects.requireNonNull(event, "event");
        SourceLocation location = Objects.requireNonNull(event.location(), "location");
        RuleTemplate kind = Objects.requireNonNull(event.kind(), "event.kind");
        String payload = location.fqcn() + "#" + location.method() + ":" + location.line()
            + ":" + kind
            + "::" + Objects.requireNonNullElse(renderedCondition, "");
        return new RuleId(stableId(payload));
    }

    public static RuleId from(SourceLocation location, RuleTemplate type) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(type, "type");
        String payload = location.fqcn() + "#" + location.method() + ":" + type;
        return new RuleId(stableId(payload));
    }

    private static String stableId(String payload) {
        UUID uuid = UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
        return uuid.toString().replace("-", "");
    }
}
