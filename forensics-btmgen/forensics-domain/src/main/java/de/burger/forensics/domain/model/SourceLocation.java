package de.burger.forensics.domain.model;

/**
 * Immutable value object describing a location inside a source file.
 */
public record SourceLocation(String fqcn, String method, int line) {
}
