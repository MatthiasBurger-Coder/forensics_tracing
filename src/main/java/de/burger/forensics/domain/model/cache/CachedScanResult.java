package de.burger.forensics.domain.model.cache;

import de.burger.forensics.domain.model.ScanEvent;
import java.util.List;
import java.util.Objects;

/**
 * Parser scan result that can be stored and restored by a cache adapter.
 */
public record CachedScanResult(SourceFileSnapshot source,
                               List<ScanEvent> events,
                               List<ScanDependency> dependencies,
                               ScanProfile profile) {
    public CachedScanResult {
        Objects.requireNonNull(source, "Source snapshot must not be null.");
        Objects.requireNonNull(events, "Scan events must not be null.");
        Objects.requireNonNull(dependencies, "Scan dependencies must not be null.");
        Objects.requireNonNull(profile, "Scan profile must not be null.");
        events = List.copyOf(events);
        dependencies = List.copyOf(dependencies);
    }
}
