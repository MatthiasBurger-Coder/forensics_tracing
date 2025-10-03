package de.burger.forensics.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Builds stable rule identifiers based on location and condition text.
 */
public final class RuleIdFactory {
    private RuleIdFactory() {
    }

    public static RuleId from(ScanEvent event, String renderedCondition) {
        Objects.requireNonNull(event, "event");
        SourceLocation location = Objects.requireNonNull(event.location(), "location");
        String payload = location.fqcn() + "#" + location.method() + ":" + location.line()
            + "::" + Objects.requireNonNullElse(renderedCondition, "");
        return new RuleId(hash(payload));
    }

    public static RuleId from(SourceLocation location, RuleTemplate type) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(type, "type");
        String payload = location.fqcn() + "#" + location.method() + ":" + type;
        return new RuleId(hash(payload));
    }

    private static String hash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 MessageDigest", e);
        }
    }
}
