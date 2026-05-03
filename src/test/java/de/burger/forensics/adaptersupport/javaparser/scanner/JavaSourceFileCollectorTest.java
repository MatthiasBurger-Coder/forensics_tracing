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

    @Test
    void scannerShouldIgnoreBuildOutputAndTestSourcesByDefault(@TempDir Path tempDir) throws IOException {
        Path mainSource = writeJavaFile(tempDir.resolve("src/main/java/com/example/MainSample.java"));
        writeJavaFile(tempDir.resolve("src/test/java/com/example/TestSample.java"));
        writeJavaFile(tempDir.resolve("src/integrationTest/java/com/example/IntegrationSample.java"));
        writeJavaFile(tempDir.resolve("target/generated-sources/com/example/TargetSample.java"));
        writeJavaFile(tempDir.resolve("build/generated/sources/com/example/BuildSample.java"));
        writeJavaFile(tempDir.resolve("out/generated/com/example/OutSample.java"));
        writeJavaFile(tempDir.resolve(".git/hooks/GitSample.java"));
        writeJavaFile(tempDir.resolve(".gradle/cache/GradleSample.java"));
        writeJavaFile(tempDir.resolve(".idea/scratches/IdeaSample.java"));

        List<Path> files = collector.collect(tempDir);

        assertThat(files)
                .containsExactly(mainSource);
    }

    @Test
    void shouldKeepBuildNamedPackagesBelowMainSources(@TempDir Path tempDir) throws IOException {
        Path source = writeJavaFile(tempDir.resolve("src/main/java/com/example/build/BuildPackageSample.java"));
        writeJavaFile(tempDir.resolve("module/build/generated/com/example/GeneratedSample.java"));

        List<Path> files = collector.collect(tempDir);

        assertThat(files)
                .containsExactly(source);
    }

    @Test
    void shouldKeepExplicitTestSourceRootScannable(@TempDir Path tempDir) throws IOException {
        Path source = writeJavaFile(tempDir.resolve("src/test/java/com/example/ExplicitTestSample.java"));

        List<Path> files = collector.collect(tempDir.resolve("src/test/java"));

        assertThat(files)
                .containsExactly(source);
    }

    private static Path writeJavaFile(Path javaFile) throws IOException {
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class " + javaFile.getFileName().toString().replace(".java", "") + " {}");
        return javaFile;
    }
}
