package de.burger.forensics.domain.model.entry;

import java.util.List;

/**
 * Metadata about a discovered method.
 * This entry keeps file-level and class-level information separate from the MethodScanContext.
 */
public record MethodEntry(
        String fullyQualifiedMethodName,
        String className,
        String methodName,
        List<String> parameterTypes,
        String returnType
) {}
