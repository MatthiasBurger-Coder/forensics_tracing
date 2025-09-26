package de.burger.forensics.plugin.scan;

import de.burger.forensics.plugin.scan.java.JavaAstScanner;
import de.burger.forensics.plugin.scan.kotlin.KotlinAstScanner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// English comments only in code.
public final class ScannerFacade {
    private final SourceScanner javaScanner = new JavaAstScanner();
    private final SourceScanner kotlinScanner = new KotlinAstScanner();

    public List<ScanEvent> scan(Path root, List<String> includePkgs, List<String> excludePkgs) {
        var events = new ArrayList<ScanEvent>();
        events.addAll(javaScanner.scan(root, includePkgs, excludePkgs));
        events.addAll(kotlinScanner.scan(root, includePkgs, excludePkgs));
        return events;
    }
}
