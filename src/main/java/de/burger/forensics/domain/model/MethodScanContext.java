package de.burger.forensics.domain.model;

import java.util.*;

/**
 * Represents the analysis state for a single method.
 * <p>
 * A MethodScanContext is created per method and holds:
 * <ul>
 *     <li>basic method metadata (id, class, name, signature)</li>
 *     <li>optional source file and line range information</li>
 *     <li>all ScanEvents related to this method</li>
 *     <li>an attribute map for additional step-specific data</li>
 * </ul>
 *
 * This type intentionally does not expose any parser or adapter-specific types.
 * It is part of the domain model and can be used by multiple adapters.
 */
public final class MethodScanContext {

    /** Fully qualified method identifier, e.g. "com.example.MyClass#doSomething(String,int)" */
    private final String methodId;

    /** Simple class name without package */
    private final String className;

    /** Simple method name */
    private final String methodName;

    /** Parameter type names in declaration order */
    private final List<String> parameterTypes;

    /** Return type name, may be "void" */
    private final String returnType;

    /** Optional source file path as String (to keep domain free from Path) */
    private final String sourceFile;

    /** Optional line where the method starts, -1 if unknown */
    private final int lineStart;

    /** Optional line where the method ends, -1 if unknown */
    private final int lineEnd;

    /** All events associated with this method */
    private final List<ScanEvent> events = new ArrayList<>();

    /** Additional attributes for pipeline or step specific data */
    private final Map<String, Object> attributes = new HashMap<>();


    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public MethodScanContext(
            String methodId,
            String className,
            String methodName,
            List<String> parameterTypes,
            String returnType
    ) {
        this(methodId, className, methodName, parameterTypes, returnType, null, -1, -1);
    }

    public MethodScanContext(
            String methodId,
            String className,
            String methodName,
            List<String> parameterTypes,
            String returnType,
            String sourceFile,
            int lineStart,
            int lineEnd
    ) {
        this.methodId = Objects.requireNonNull(methodId, "methodId must not be null");
        this.className = Objects.requireNonNull(className, "className must not be null");
        this.methodName = Objects.requireNonNull(methodName, "methodName must not be null");
        this.parameterTypes = List.copyOf(Objects.requireNonNull(parameterTypes, "parameterTypes must not be null"));
        this.returnType = Objects.requireNonNull(returnType, "returnType must not be null");
        this.sourceFile = sourceFile;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
    }


    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    /**
     * Adds a ScanEvent that is related to this method.
     */
    public void addEvent(ScanEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        events.add(event);
    }

    /**
     * Returns all events for this method as an unmodifiable view.
     */
    public List<ScanEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }


    // -------------------------------------------------------------------------
    // Attribute handling
    // -------------------------------------------------------------------------

    /**
     * Stores a custom attribute in this method context.
     * This can be used by pipeline steps to attach additional information.
     */
    public void putAttribute(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        attributes.put(key, value);
    }

    /**
     * Returns a custom attribute or null if not present.
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Returns an unmodifiable view of all attributes.
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }


    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getMethodId() {
        return methodId;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public String getReturnType() {
        return returnType;
    }

    public Optional<String> getSourceFile() {
        return Optional.ofNullable(sourceFile);
    }

    public int getLineStart() {
        return lineStart;
    }

    public int getLineEnd() {
        return lineEnd;
    }
}
