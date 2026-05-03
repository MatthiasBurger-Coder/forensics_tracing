package de.burger.forensics.domain.model;

import java.util.Objects;

/**
 * Source metadata captured while scanning a condition expression.
 */
public record SourceContext(String packageName,
                            String sourceFilePath,
                            String fullyQualifiedClassName,
                            String simpleClassName,
                            String methodName,
                            String methodSignature) {

    private static final SourceContext EMPTY = new SourceContext("", "", "", "", "", "");

    public SourceContext {
        packageName = Objects.requireNonNullElse(packageName, "");
        sourceFilePath = Objects.requireNonNullElse(sourceFilePath, "");
        fullyQualifiedClassName = Objects.requireNonNullElse(fullyQualifiedClassName, "");
        simpleClassName = Objects.requireNonNullElse(simpleClassName, "");
        methodName = Objects.requireNonNullElse(methodName, "");
        methodSignature = Objects.requireNonNullElse(methodSignature, "");
    }

    public static SourceContext empty() {
        return EMPTY;
    }
}
