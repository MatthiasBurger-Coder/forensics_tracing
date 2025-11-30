package de.burger.forensics.application;

import de.burger.forensics.domain.model.MethodScanContext;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.entry.ErrorEntry;
import de.burger.forensics.domain.model.entry.EventEntry;
import de.burger.forensics.domain.model.entry.FileEntry;
import de.burger.forensics.domain.model.entry.MethodEntry;
import de.burger.forensics.domain.model.entry.WarningEntry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Global context container for a single forensic analysis run.
 * <p>
 * The context acts as the shared state between all AnalysisSteps
 * in the application layer pipeline. It collects:
 * <ul>
 *     <li>source roots and discovered files</li>
 *     <li>scan events and event entries</li>
 *     <li>method level information</li>
 *     <li>warnings and errors</li>
 *     <li>generic settings used to exchange state between steps</li>
 * </ul>
 */
public final class AnalysisContext {

    // -------------------------------------------------------------------------
    // General metadata
    // -------------------------------------------------------------------------

    /** Source directories to inspect */
    private final List<Path> sourceRoots = new ArrayList<>();

    /** Time information for the entire analysis run */
    private final Instant startTime = Instant.now();
    private Instant endTime;

    /** Optional configuration map for pipeline steps */
    private final Map<String, Object> settings = new HashMap<>();


    // -------------------------------------------------------------------------
    // File and method related data
    // -------------------------------------------------------------------------

    /** All discovered files (can be directories or concrete files) */
    private final List<FileEntry> fileEntries = new ArrayList<>();

    /** High level method metadata (separate from MethodScanContext) */
    private final List<MethodEntry> methodEntries = new ArrayList<>();

    /**
     * Detailed scan context per method.
     * Key is usually the fully qualified method name.
     */
    private final Map<String, MethodScanContext> methodContexts = new HashMap<>();


    // -------------------------------------------------------------------------
    // Events and trace related data
    // -------------------------------------------------------------------------

    /** Raw scan events produced by scanners or transformers */
    private final List<ScanEvent> events = new ArrayList<>();

    /**
     * Event entries that wrap ScanEvents with additional metadata
     * such as source or index for export and debugging.
     */
    private final List<EventEntry> eventEntries = new ArrayList<>();


    // -------------------------------------------------------------------------
    // Diagnostics: warnings and errors
    // -------------------------------------------------------------------------

    /** Non-fatal issues encountered during analysis */
    private final List<WarningEntry> warnings = new ArrayList<>();

    /** Fatal or critical problems that may interrupt the analysis */
    private final List<ErrorEntry> errors = new ArrayList<>();


    // -------------------------------------------------------------------------
    // Add operations
    // -------------------------------------------------------------------------

    /** Adds a new source root directory to the analysis */
    public void addSourceRoot(Path root) {
        Objects.requireNonNull(root, "root must not be null");
        sourceRoots.add(root);
    }

    /** Adds a discovered file entry */
    public void addFileEntry(FileEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        fileEntries.add(entry);
    }

    /** Adds a method metadata entry */
    public void addMethodEntry(MethodEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        methodEntries.add(entry);
    }

    /** Registers or updates a method scan context */
    public void putMethodContext(String methodId, MethodScanContext context) {
        Objects.requireNonNull(methodId, "methodId must not be null");
        Objects.requireNonNull(context, "context must not be null");
        methodContexts.put(methodId, context);
    }

    /** Adds a raw scan event */
    public void addEvent(ScanEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        events.add(event);
    }

    /**
     * Adds an event entry that wraps a scan event with additional metadata.
     */
    public void addEventEntry(EventEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        eventEntries.add(entry);
    }

    /**
     * Convenience method: add a scan event and automatically wrap it
     * into an EventEntry for trace export.
     */
    public void addEventWithSource(ScanEvent event, String source) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(source, "source must not be null");
        events.add(event);
        eventEntries.add(new EventEntry(event, source, eventEntries.size()));
    }

    /** Adds a non-fatal warning */
    public void addWarning(WarningEntry warning) {
        Objects.requireNonNull(warning, "warning must not be null");
        warnings.add(warning);
    }

    /** Adds a fatal or critical error */
    public void addError(ErrorEntry error) {
        Objects.requireNonNull(error, "error must not be null");
        errors.add(error);
    }

    /** Stores custom settings to allow pipeline steps to exchange state */
    public void putSetting(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        settings.put(key, value);
    }


    // -------------------------------------------------------------------------
    // Read operations (immutable views)
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

    public Instant getStartTime() {
        return startTime;
    }

    public Optional<Instant> getEndTime() {
        return Optional.ofNullable(endTime);
    }


    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Marks the analysis as finished */
    public void markFinished() {
        this.endTime = Instant.now();
    }

    /** Returns true if the analysis has been marked as finished */
    public boolean isFinished() {
        return endTime != null;
    }
}
