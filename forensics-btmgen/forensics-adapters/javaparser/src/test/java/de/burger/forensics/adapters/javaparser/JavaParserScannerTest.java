package de.burger.forensics.adapters.javaparser;

import static org.assertj.core.api.Assertions.assertThat;

import de.burger.forensics.domain.model.RuleType;
import de.burger.forensics.domain.model.ScanEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaParserScannerTest {

    private final JavaParserScanner scanner = new JavaParserScanner();

    @Test
    void scansIfStatements() throws IOException {
        Path tempDir = Files.createTempDirectory("scanner-test");
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, """
            package example;
            public class Sample {
              public void run(int value) {
                if (value > 0) {
                  System.out.println(value);
                }
              }
            }
            """
        );

        List<ScanEvent> events = scanner.scan(tempDir).toList();
        assertThat(events)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.kind()).isEqualTo(RuleType.IF_TRUE);
                assertThat(event.location().fqcn()).isEqualTo("example.Sample");
            });
    }
}
