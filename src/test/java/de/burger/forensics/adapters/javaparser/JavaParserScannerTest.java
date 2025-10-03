package de.burger.forensics.adapters.javaparser;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
                assertThat(event.kind()).isEqualTo(RuleTemplate.IF_TRUE);
                assertThat(event.location().fqcn()).isEqualTo("example.Sample");
            });
    }

    @Test
    void scansSwitchReturnAndThrowStatements() throws IOException {
        Path tempDir = Files.createTempDirectory("scanner-test-2");
        Path source = tempDir.resolve("Advanced.java");
        Files.writeString(source, """
            package example;
            public class Advanced {
              public int compute(int value) {
                if (value > 10) {
                  throw new IllegalArgumentException();
                } else if (value < 0) {
                  return -1;
                }
                switch (value) {
                  case 1 -> value = 2;
                  default -> value = 3;
                }
                return value;
              }
            }
            """
        );

        List<ScanEvent> events = scanner.scan(tempDir).toList();

        assertThat(events).extracting(ScanEvent::kind)
            .contains(RuleTemplate.IF_TRUE, RuleTemplate.IF_FALSE, RuleTemplate.SWITCH, RuleTemplate.SWITCH_CASE, RuleTemplate.RETURN, RuleTemplate.THROW);
    }
}
