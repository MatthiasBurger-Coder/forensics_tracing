package de.burger.forensics.plugin.scan;

import de.burger.forensics.plugin.scan.java.JavaAstScanner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// English comments only in code.
public final class ScannerFacade {
    private final SourceScanner javaScanner = new JavaAstScanner();

    public List<ScanEvent> scan(Path root, List<String> includePkgs, List<String> excludePkgs) {
        var events = new ArrayList<ScanEvent>();
        events.addAll(javaScanner.scan(root, includePkgs, excludePkgs));
        return events;
    }
}
