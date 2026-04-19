package de.burger.forensics.application;

import de.burger.forensics.domain.model.MethodScanContext;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.entry.ErrorEntry;
import de.burger.forensics.domain.model.entry.EventEntry;
import de.burger.forensics.domain.model.entry.FileEntry;
import de.burger.forensics.domain.model.entry.MethodEntry;
import de.burger.forensics.domain.model.entry.WarningEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Global context container for a single forensic analysis run.
 * <p>
 * The context is shared between all AnalysisSteps in the application-layer pipeline
 * and collects files, methods, events, warnings, errors and generic settings.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisContext {
    private static final String ENTRY_MUST_NOT_BE_NULL = "entry must not be null";

    // -------------------------------------------------------------------------
    // General metadata
    // -------------------------------------------------------------------------

    @Builder.Default
    private List<Path> sourceRoots = new ArrayList<>();

    @Builder.Default
    private Instant startTime = Instant.now();

    private Instant endTime;

    @Builder.Default
    private Map<String, Object> settings = new HashMap<>();


    // -------------------------------------------------------------------------
    // Files and methods
    // -------------------------------------------------------------------------

    @Builder.Default
    private List<FileEntry> fileEntries = new ArrayList<>();

    @Builder.Default
    private List<MethodEntry> methodEntries = new ArrayList<>();

    @Builder.Default
    private Map<String, MethodScanContext> methodContexts = new HashMap<>();


    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @Builder.Default
    private List<ScanEvent> events = new ArrayList<>();

    @Builder.Default
    private List<EventEntry> eventEntries = new ArrayList<>();


    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    @Builder.Default
    private List<WarningEntry> warnings = new ArrayList<>();

    @Builder.Default
    private List<ErrorEntry> errors = new ArrayList<>();


    // -------------------------------------------------------------------------
    // Mutating operations (controlled write access)
    // -------------------------------------------------------------------------

    public void addSourceRoot(Path root) {
        Objects.requireNonNull(root, "root must not be null");
        sourceRoots.add(root);
    }

    public void addFileEntry(FileEntry entry) {
        Objects.requireNonNull(entry, ENTRY_MUST_NOT_BE_NULL);
        fileEntries.add(entry);
    }

    public void addMethodEntry(MethodEntry entry) {
        Objects.requireNonNull(entry, ENTRY_MUST_NOT_BE_NULL);
        methodEntries.add(entry);
    }

    public void putMethodContext(String methodId, MethodScanContext context) {
        Objects.requireNonNull(methodId, "methodId must not be null");
        Objects.requireNonNull(context, "context must not be null");
        methodContexts.put(methodId, context);
    }

    public void addMethodContext(String methodId,
                                 String className,
                                 String methodName,
                                 List<String> parameterTypes,
                                 String returnType,
                                 List<ScanEvent> events) {
        Objects.requireNonNull(methodId, "methodId must not be null");
        Objects.requireNonNull(className, "className must not be null");
        Objects.requireNonNull(methodName, "methodName must not be null");
        Objects.requireNonNull(parameterTypes, "parameterTypes must not be null");
        Objects.requireNonNull(events, "events must not be null");

        MethodScanContext context = MethodScanContext.builder()
            .methodId(methodId)
            .className(className)
            .methodName(methodName)
            .parameterTypes(new ArrayList<>(parameterTypes))
            .returnType(returnType)
            .events(new ArrayList<>(events))
            .build();

        methodContexts.put(methodId, context);
        methodEntries.add(new MethodEntry(methodId, className, methodName, List.copyOf(parameterTypes), returnType));
    }

    public void addEvent(ScanEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        events.add(event);
    }

    public void addEventEntry(EventEntry entry) {
        Objects.requireNonNull(entry, ENTRY_MUST_NOT_BE_NULL);
        eventEntries.add(entry);
    }

    public void addEventWithSource(ScanEvent event, String source) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(source, "source must not be null");
        events.add(event);
        eventEntries.add(new EventEntry(event, source, eventEntries.size()));
    }

    public void addWarning(WarningEntry warning) {
        Objects.requireNonNull(warning, "warning must not be null");
        warnings.add(warning);
    }

    public void addError(ErrorEntry error) {
        Objects.requireNonNull(error, "error must not be null");
        errors.add(error);
    }

    public void putSetting(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        settings.put(key, value);
    }


    // -------------------------------------------------------------------------
    // Immutable views (override Lombok-generated getters)
    // -------------------------------------------------------------------------

    public List<Path> getSourceRoots() {
        return Collections.unmodifiableList(sourceRoots);
    }

    public List<FileEntry> getFileEntries() {
        return Collections.unmodifiableList(fileEntries);
    }

    public List<MethodEntry> getMethodEntries() {
        return Collections.unmodifiableList(methodEntries);
    }

    public Map<String, MethodScanContext> getMethodContexts() {
        return Collections.unmodifiableMap(methodContexts);
    }

    public List<ScanEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<EventEntry> getEventEntries() {
        return Collections.unmodifiableList(eventEntries);
    }

    public List<WarningEntry> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<ErrorEntry> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public Map<String, Object> getSettings() {
        return Collections.unmodifiableMap(settings);
    }


    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Marks the analysis as finished and records the end time.
     */
    public void markFinished() {
        this.endTime = Instant.now();
    }

    /**
     * Returns true if the analysis has been finished.
     */
    public boolean isFinished() {
        return endTime != null;
    }
}
