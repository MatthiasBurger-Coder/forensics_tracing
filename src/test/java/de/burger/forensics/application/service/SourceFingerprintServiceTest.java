package de.burger.forensics.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFingerprintServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsStableFingerprintForUnchangedFiles() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot.resolve("com/example"));
        Files.writeString(sourceRoot.resolve("com/example/Demo.java"), "class Demo {}\n");

        SourceFingerprintService service = new SourceFingerprintService();

        SourceFingerprintResult first = service.fingerprint(List.of(sourceRoot));
        SourceFingerprintResult second = service.fingerprint(List.of(sourceRoot));

        assertThat(first.sourceFingerprint()).isEqualTo(second.sourceFingerprint());
        assertThat(first.sourceFiles()).extracting(file -> file.relativePath())
                .containsExactly("com/example/Demo.java");
    }

    @Test
    void changedContentChangesFingerprintAndNonJavaFilesAreIgnored() throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Path javaFile = sourceRoot.resolve("Demo.java");
        Files.writeString(javaFile, "class Demo {}\n");
        Files.writeString(sourceRoot.resolve("README.md"), "ignored");

        SourceFingerprintService service = new SourceFingerprintService();
        String before = service.fingerprint(List.of(sourceRoot)).sourceFingerprint().value();

        Files.writeString(javaFile, "class Demo { int value; }\n");

        SourceFingerprintResult after = service.fingerprint(List.of(sourceRoot));
        assertThat(after.sourceFingerprint().value()).isNotEqualTo(before);
        assertThat(after.sourceFiles()).hasSize(1);
    }

    @Test
    void relativePathsUseForwardSlashesAcrossRoots() throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/Sample.java"), "class Sample {}\n");

        SourceFingerprintResult result = new SourceFingerprintService().fingerprint(List.of(root));

        assertThat(result.sourceFiles()).extracting(file -> file.relativePath())
                .containsExactly("a/b/Sample.java");
    }

    @Test
    void acceptsSingleJavaFileRootsAndIgnoresMissingOrNonJavaRoots() throws IOException {
        Path javaFile = tempDir.resolve("Single.java");
        Path textFile = tempDir.resolve("notes.txt");
        Files.writeString(javaFile, "class Single {}\n");
        Files.writeString(textFile, "ignored");

        List<Path> roots = new java.util.ArrayList<>();
        roots.add(javaFile);
        roots.add(textFile);
        roots.add(tempDir.resolve("missing"));
        roots.add(null);

        SourceFingerprintResult result = new SourceFingerprintService().fingerprint(roots);

        assertThat(result.sourceFiles()).extracting(file -> file.relativePath())
                .containsExactly("Single.java");
    }
}
