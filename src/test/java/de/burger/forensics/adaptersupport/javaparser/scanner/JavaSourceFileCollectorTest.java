package de.burger.forensics.adaptersupport.javaparser.scanner;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSourceFileCollectorTest {

    private final JavaSourceFileCollector collector = new JavaSourceFileCollector();

    @Test
    void skipsSymbolicLinkDirectories(@TempDir Path tempDir) throws IOException {
        Path scanRoot = Files.createDirectory(tempDir.resolve("scan-root"));
        Path realDir = Files.createDirectory(tempDir.resolve("external-real"));
        Path linkedDir = scanRoot.resolve("linked");
        try {
            Files.createSymbolicLink(linkedDir, realDir);
        } catch (IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are not supported or permitted in this environment.");
        }

        Path rootFile = scanRoot.resolve("Root.java");
        Files.writeString(rootFile, "class Root {}");
        Files.writeString(realDir.resolve("Linked.java"), "class Linked {}");

        List<Path> files = collector.collect(scanRoot);

        assertThat(files)
            .containsExactly(rootFile);
    }
}
