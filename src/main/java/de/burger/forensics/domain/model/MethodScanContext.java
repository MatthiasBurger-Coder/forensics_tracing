package de.burger.forensics.domain.model;

import lombok.*;

import java.util.*;

/**
 * Holds all analysis-related information about a single method.
 * <p>
 * This type is part of the domain layer and is populated by
 * adapter-side scanners (e.g., JavaParser) but remains free of
 * adapter-specific types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodScanContext {

    /**
     * Fully qualified method identifier,
     * e.g. "com.example.MyClass#doSomething(String,int)"
     */
    private String methodId;

    /** Simple class name without package */
    private String className;

    /**
     * Method name without class prefix
     */
    private String methodName;

    /** Parameter type names in declaration order */
    @Builder.Default
    private List<String> parameterTypes = new ArrayList<>();

    /** Return type name, may be "void" */
    private String returnType;

    /**
     * Optional source file path
     */
    private String sourceFile;

    /**
     * Optional line where the method starts
     */
    @Builder.Default
    private int lineStart = -1;

    /**
     * Optional line where the method ends
     */
    @Builder.Default
    private int lineEnd = -1;

    /**
     * All events associated with this method
     */
    @Builder.Default
    private List<ScanEvent> events = new ArrayList<>();

    /**
     * Additional attributes for pipeline or step-specific data
     */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();


    // -------------------------------------------------------------------------
    // Convenience helpers
    // -------------------------------------------------------------------------

    public void addEvent(ScanEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        events.add(event);
    }

    public void putAttribute(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        attributes.put(key, value);
    }
}
