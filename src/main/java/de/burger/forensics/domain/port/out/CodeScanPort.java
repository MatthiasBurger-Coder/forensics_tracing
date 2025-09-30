package de.burger.forensics.domain.port.out;

import de.burger.forensics.domain.model.ScanEvent;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Port for scanning codebases and returning a stream of domain events.
 */
public interface CodeScanPort {
    Stream<ScanEvent> scan(Path root);
}
