package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.cache.CachedScanResult;
import de.burger.forensics.domain.model.cache.SourceFileSnapshot;
import java.util.Optional;
import java.util.Set;

/**
 * Port for loading and storing parser scan cache results.
 */
public interface ScanCachePort {
    void initialize();

    Optional<CachedScanResult> find(SourceFileSnapshot source);

    void store(CachedScanResult result);

    void deleteMissing(String rootPath, Set<String> currentRelativePaths);

    void rebuild();
}
