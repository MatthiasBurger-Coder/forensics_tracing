package de.burger.forensics.plugin.scan;

import java.nio.file.Path;
import java.util.List;

// English comments only in code.
public interface SourceScanner {
    List<ScanEvent> scan(Path root, List<String> includePkgs, List<String> excludePkgs);
}
