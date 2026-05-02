package de.burger.forensics.domain.model.cache;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated timing and cache counters for a scan operation.
 */
public record ScanProfile(Map<ScanPhase, Duration> phaseDurations,
                          int totalFiles,
                          int parsedFiles,
                          int cacheHitFiles,
                          int cacheMissFiles,
                          int failedFiles,
                          int totalMethods,
                          int totalEvents,
                          int totalDependencies) {
    public ScanProfile {
        Objects.requireNonNull(phaseDurations, "Phase durations must not be null.");
        phaseDurations = Map.copyOf(phaseDurations);
        if (totalFiles < 0
                || parsedFiles < 0
                || cacheHitFiles < 0
                || cacheMissFiles < 0
                || failedFiles < 0
                || totalMethods < 0
                || totalEvents < 0
                || totalDependencies < 0) {
            throw new IllegalArgumentException("Scan profile counters must not be negative.");
        }
    }

    public static ScanProfile empty() {
        return new ScanProfile(Map.of(), 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public ScanProfile plus(ScanProfile other) {
        Objects.requireNonNull(other, "Other scan profile must not be null.");
        EnumMap<ScanPhase, Duration> mergedDurations = new EnumMap<>(ScanPhase.class);
        phaseDurations.forEach(mergedDurations::put);
        other.phaseDurations.forEach((phase, duration) ->
                mergedDurations.merge(phase, duration, Duration::plus));
        return new ScanProfile(
                mergedDurations,
                totalFiles + other.totalFiles,
                parsedFiles + other.parsedFiles,
                cacheHitFiles + other.cacheHitFiles,
                cacheMissFiles + other.cacheMissFiles,
                failedFiles + other.failedFiles,
                totalMethods + other.totalMethods,
                totalEvents + other.totalEvents,
                totalDependencies + other.totalDependencies);
    }
}
