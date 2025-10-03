package de.burger.forensics.infrastructure.rt;

public record RtSpanToken(String spanId, String name, long startedAtNanos) {
    public static final RtSpanToken NOOP = new RtSpanToken("noop", "noop", 0L);
}
