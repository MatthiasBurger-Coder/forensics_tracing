package de.burger.forensics.adaptersupport.javaparser.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Collects Java source files while skipping symbolic-link directories.
 */
public final class JavaSourceFileCollector {

    public List<Path> collect(Path root) {
        try (Stream<Path> paths = Files.walk(root, 64)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .sorted()
                .toList();
        } catch (IOException ignored) {
            // Ignore traversal failures to avoid failing the build on single-file issues.
            return List.of();
        }
    }
}
