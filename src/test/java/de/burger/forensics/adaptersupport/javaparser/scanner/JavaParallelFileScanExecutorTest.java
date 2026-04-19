package de.burger.forensics.adaptersupport.javaparser.scanner;

import de.burger.forensics.adaptersupport.javaparser.DefaultConditionRenderingStrategy;
import de.burger.forensics.adaptersupport.javaparser.InstanceFieldNormalizer;
import de.burger.forensics.adaptersupport.javaparser.MethodEventExtractor;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParallelFileScanExecutorTest {

    private final JavaParallelFileScanExecutor executor = new JavaParallelFileScanExecutor();
    private final JavaParserFactory parserFactory = new JavaParserFactory();
    private final MethodEventExtractor extractor =
        new MethodEventExtractor(new DefaultConditionRenderingStrategy(new InstanceFieldNormalizer()));

    @Test
    void returnsEmptyWhenNoSourceFilesExist(@TempDir Path tempDir) {
        assertThat(executor.scan(tempDir, List.of(), parserFactory, extractor)).isEmpty();
    }

    @Test
    void scansSingleBatchesWithoutParallelFallback(@TempDir Path tempDir) throws IOException {
        Path source = writeSource(tempDir.resolve("Sample.java"), "example.Sample");

        List<ScanEvent> events = executor.scan(tempDir, List.of(source), parserFactory, extractor);

        assertThat(events).extracting(ScanEvent::kind)
            .containsExactlyInAnyOrder(RuleTemplate.IF_TRUE, RuleTemplate.IF_FALSE, RuleTemplate.RETURN, RuleTemplate.RETURN);
    }

    @Test
    void fallsBackToSequentialScanningWhenTheCurrentThreadIsInterrupted(@TempDir Path tempDir) throws IOException {
        Path first = writeSource(tempDir.resolve("First.java"), "example.First");
        Path second = writeSource(tempDir.resolve("Second.java"), "example.Second");

        Thread.currentThread().interrupt();
        try {
            List<ScanEvent> events = executor.scan(tempDir, List.of(first, second), parserFactory, extractor);

            assertThat(events).hasSize(8);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void scansMultipleFilesWithParallelWorkers(@TempDir Path tempDir) throws IOException {
        Path first = writeSource(tempDir.resolve("First.java"), "example.First");
        Path second = writeSource(tempDir.resolve("Second.java"), "example.Second");

        List<ScanEvent> events = executor.scan(tempDir, List.of(first, second), parserFactory, extractor);

        assertThat(events).hasSize(8);
    }

    @Test
    void partitionsSourceFilesRoundRobin() throws Exception {
        Method method = JavaParallelFileScanExecutor.class.getDeclaredMethod("partition", List.class, int.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<List<Path>> batches = (List<List<Path>>) method.invoke(
            executor,
            List.of(Path.of("A.java"), Path.of("B.java"), Path.of("C.java"), Path.of("D.java"), Path.of("E.java")),
            3
        );

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).containsExactly(Path.of("A.java"), Path.of("D.java"));
        assertThat(batches.get(1)).containsExactly(Path.of("B.java"), Path.of("E.java"));
        assertThat(batches.get(2)).containsExactly(Path.of("C.java"));
    }

    private static Path writeSource(Path source, String fqcn) throws IOException {
        String pkg = fqcn.substring(0, fqcn.lastIndexOf('.'));
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        Files.writeString(source, """
            package %s;
            class %s {
                boolean run(int value) {
                    if (value > 0) {
                        return true;
                    }
                    return false;
                }
            }
            """.formatted(pkg, simpleName));
        return source;
    }
}
