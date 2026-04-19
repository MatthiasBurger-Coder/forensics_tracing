package de.burger.forensics.adaptersupport.javaparser.scanner;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserFactoryTest {

    private final JavaParserFactory factory = new JavaParserFactory();

    @Test
    void createsParserForDirectoryRoots(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, "class Sample { void run() {} }");

        var result = factory.create(tempDir).parse(source);

        assertThat(result.getResult()).isPresent();
        assertThat(result.getResult().orElseThrow().findFirst(MethodDeclaration.class)).isPresent();
    }

    @Test
    void createsParserForSingleFileRootsUsingTheParentDirectory(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, "class Sample { void run() {} }");

        var result = factory.create(source).parse(source);

        assertThat(result.getResult()).isPresent();
    }

    @Test
    void createsParserWhenTheRootParentDoesNotExist() {
        Path root = Path.of("missing-root").resolve("Sample.java");

        assertThat(factory.create(root)).isNotNull();
    }

    @Test
    void createsParserForRelativeFileRootsWithoutParent() {
        assertThat(factory.create(Path.of("Sample.java"))).isNotNull();
    }
}
