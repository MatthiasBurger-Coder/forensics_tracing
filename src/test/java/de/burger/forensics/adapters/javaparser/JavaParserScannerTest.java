package de.burger.forensics.adapters.javaparser;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserScannerTest {

    private final JavaParserScanner scanner = new JavaParserScanner();

    @Test
    void scansIfStatements(@TempDir Path tempDir) throws IOException {
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
        assertThat(events).hasSize(2);
        assertThat(events).extracting(ScanEvent::kind)
                .containsExactlyInAnyOrder(RuleTemplate.IF_TRUE, RuleTemplate.IF_FALSE);
        assertThat(events).allSatisfy(event ->
                assertThat(event.location().fqcn()).isEqualTo("example.Sample"));
    }

    @Test
    void scansSwitchReturnAndThrowStatements(@TempDir Path tempDir) throws IOException {
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

    @Test
    void prefixesInstanceFieldAccessWithThisPlaceholder(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("SwitchingOrderApi.java");
        Files.writeString(source, """
            package example;
            import java.util.function.Predicate;
            public class SwitchingOrderApi {
              private final Policy policy = new Policy();
              public boolean sumGross(String orderId) {
                if (policy.newEnabled() && policy.routePredicate().test(orderId)) {
                  return true;
                }
                return false;
              }
              static final class Policy {
                boolean newEnabled() { return true; }
                Predicate<String> routePredicate() { return value -> value.isEmpty(); }
              }
            }
            """);

        List<ScanEvent> events = scanner.scan(tempDir).toList();

        assertThat(events)
            .filteredOn(event -> event.kind() == RuleTemplate.IF_TRUE || event.kind() == RuleTemplate.IF_FALSE)
            .extracting(ScanEvent::conditionText)
            .contains("$this.policy.newEnabled() && $this.policy.routePredicate().test($1)");
    }

    @Test
    void prefixesLocalVariableAccessWithDollarPlaceholder(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("PolicyService.java");
        Files.writeString(source, """
            package example;
            import java.util.function.Predicate;
            public class PolicyService {
              public boolean process(String orderId) {
                Policy policy = new Policy();
                if (policy.routePredicate().test(orderId)) {
                  return true;
                }
                return false;
              }
              static final class Policy {
                Predicate<String> routePredicate() { return value -> value.startsWith("A"); }
              }
            }
            """);

        List<ScanEvent> events = scanner.scan(tempDir).toList();

        assertThat(events)
            .filteredOn(event -> event.kind() == RuleTemplate.IF_TRUE || event.kind() == RuleTemplate.IF_FALSE)
            .extracting(ScanEvent::conditionText)
            .contains("$policy.routePredicate().test($1)");
    }

    @Test
    void skipsSymbolicLinkDirectories(@TempDir Path tempDir) throws IOException {
        Path scanRoot = Files.createDirectory(tempDir.resolve("scan-root"));
        Path realDir = Files.createDirectory(tempDir.resolve("external-real"));
        Path linkedDir = scanRoot.resolve("linked");
        try {
            Files.createSymbolicLink(linkedDir, realDir);
        } catch (IOException | UnsupportedOperationException exception) {
            // Skip when the environment does not allow creating symbolic links.
            Assumptions.assumeTrue(false, "Symbolic links are not supported or permitted in this environment.");
        }

        Files.writeString(scanRoot.resolve("Root.java"), """
            package root;
            public class Root {
              public void run(int value) {
                if (value > 1) {
                  System.out.println(value);
                }
              }
            }
            """);

        Files.writeString(realDir.resolve("Linked.java"), """
            package linked;
            public class Linked {
              public void run(int value) {
                if (value > 2) {
                  System.out.println(value);
                }
              }
            }
            """);

        List<ScanEvent> events = scanner.scan(scanRoot).toList();

        assertThat(events)
            .extracting(event -> event.location().fqcn())
            .contains("root.Root")
            .doesNotContain("linked.Linked");
    }

    @Test
    void ignoresNonJavaFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("notes.txt"), "not java");

        List<ScanEvent> events = scanner.scan(tempDir).toList();

        assertThat(events).isEmpty();
    }

    @Test
    void ignoresParseErrors(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Broken.java"), "class {");

        List<ScanEvent> events = scanner.scan(tempDir).toList();

        assertThat(events).isEmpty();
    }

    @Test
    void ignoresMissingRootPath() {
        Path missing = Path.of("missing-source.java");

        List<ScanEvent> events = scanner.scan(missing).toList();

        assertThat(events).isEmpty();
    }

    @Test
    void ignoresRootsWithMissingParentDirectory(@TempDir Path tempDir) {
        Path missingChild = tempDir.resolve("missing").resolve("Sample.java");

        List<ScanEvent> events = scanner.scan(missingChild).toList();

        assertThat(events).isEmpty();
    }

    @Test
    void scansWhenRootIsAFile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Single.java");
        Files.writeString(source, """
            package example;
            public class Single {
              public void run(int value) {
                if (value > 0) {
                  System.out.println(value);
                }
              }
            }
            """);

        List<ScanEvent> events = scanner.scan(source).toList();

        assertThat(events).hasSize(2);
    }

    @Test
    void supportsConcurrentScans(@TempDir Path tempDir) throws Exception {
        Path left = Files.createDirectory(tempDir.resolve("left"));
        Path right = Files.createDirectory(tempDir.resolve("right"));

        Files.writeString(left.resolve("Left.java"), """
            package example.left;
            public class Left {
              public void run(int value) {
                if (value > 0) {
                  System.out.println(value);
                }
              }
            }
            """);

        Files.writeString(right.resolve("Right.java"), """
            package example.right;
            public class Right {
              public void run(int value) {
                if (value > 0) {
                  System.out.println(value);
                }
              }
            }
            """);

        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<List<ScanEvent>>> tasks = List.of(
                () -> scanner.scan(left).toList(),
                () -> scanner.scan(right).toList(),
                () -> scanner.scan(left).toList(),
                () -> scanner.scan(right).toList(),
                () -> scanner.scan(left).toList(),
                () -> scanner.scan(right).toList()
            );

            var futures = executor.invokeAll(tasks);

            for (var future : futures) {
                List<ScanEvent> events = future.get(5, TimeUnit.SECONDS);
                assertThat(events).hasSize(2);
                assertThat(events).extracting(ScanEvent::kind)
                    .containsExactlyInAnyOrder(RuleTemplate.IF_TRUE, RuleTemplate.IF_FALSE);
            }
        } finally {
            executor.shutdown();
        }
    }
}
