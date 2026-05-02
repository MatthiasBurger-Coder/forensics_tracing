package de.burger.forensics.domain.model.cache;

import java.util.Arrays;

/**
 * Supported dependency categories for cached scan results.
 */
public enum DependencyKind {
    IMPORT("import"),
    EXTENDS("extends"),
    IMPLEMENTS("implements"),
    ANNOTATION("annotation"),
    THROWN_TYPE("thrown-type"),
    RETURN_TYPE("return-type"),
    PARAMETER_TYPE("parameter-type"),
    FIELD_ACCESS("field-access"),
    METHOD_CALL("method-call"),
    CONSTRUCTOR_CALL("constructor-call");

    private final String cacheToken;

    DependencyKind(String cacheToken) {
        this.cacheToken = cacheToken;
    }

    public String cacheToken() {
        return cacheToken;
    }

    public static DependencyKind fromCacheToken(String cacheToken) {
        return Arrays.stream(values())
                .filter(kind -> kind.cacheToken.equals(cacheToken))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported dependency kind: " + cacheToken));
    }
}
