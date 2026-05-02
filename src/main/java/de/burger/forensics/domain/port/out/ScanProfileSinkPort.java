package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.cache.ScanProfile;

/**
 * Port for publishing parser scan cache profile data.
 */
public interface ScanProfileSinkPort {
    void publish(ScanProfile profile);
}
