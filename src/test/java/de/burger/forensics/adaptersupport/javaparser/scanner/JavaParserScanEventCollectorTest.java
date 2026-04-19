package de.burger.forensics.adaptersupport.javaparser.scanner;

import de.burger.forensics.adaptersupport.javaparser.DefaultConditionRenderingStrategy;
import de.burger.forensics.adaptersupport.javaparser.InstanceFieldNormalizer;
import de.burger.forensics.adaptersupport.javaparser.MethodEventExtractor;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserScanEventCollectorTest {

    private final JavaParserFactory parserFactory = new JavaParserFactory();
    private final MethodEventExtractor extractor =
        new MethodEventExtractor(new DefaultConditionRenderingStrategy(new InstanceFieldNormalizer()));

    @Test
    void collectsEventsFromValidJavaFiles(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, """
            package example;
            class Sample {
                int run(int value) {
                    if (value > 0) {
                        return value;
                    }
                    return 0;
                }
            }
            """);

        List<ScanEvent> events = JavaParserScanEventCollector.collectSafely(
            parserFactory.create(tempDir),
            source,
            extractor
        );

        assertThat(events).extracting(ScanEvent::kind)
            .contains(RuleTemplate.IF_TRUE, RuleTemplate.IF_FALSE, RuleTemplate.RETURN);
    }

    @Test
    void returnsEmptyWhenParsingOrCollectionFails(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("Broken.java");
        Files.writeString(broken, "class {");

        assertThat(JavaParserScanEventCollector.collectSafely(parserFactory.create(tempDir), broken, extractor)).isEmpty();
        assertThat(JavaParserScanEventCollector.collectSafely(null, broken, extractor)).isEmpty();
        assertThat(JavaParserScanEventCollector.collectSafely(parserFactory.create(tempDir), broken, null)).isEmpty();
    }
}
