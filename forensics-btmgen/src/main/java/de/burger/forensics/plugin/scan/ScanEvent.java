package de.burger.forensics.plugin.scan;

// English comments only in code.
public record ScanEvent(
    String language,
    String fqcn,
    String method,
    String signature,
    String kind,
    int line,
    String conditionText
) {}
