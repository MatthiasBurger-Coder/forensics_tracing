// DEST: src/test/java/de/burger/forensics/plugin/scan/JavaAstScannerTest.java
package de.burger.forensics.plugin.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.burger.forensics.plugin.scan.java.JavaAstScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaAstScannerTest {

    @Test
    void emitsEventsForControlFlowConstructs() throws IOException {
        Path root = Files.createTempDirectory("java-ast");
        Path file = root.resolve("Sample.java");
        Files.writeString(file, String.join("\n",
                "package com.example;",
                "",
                "public class Sample {",
                "    public int compute(int value) {",
                "        if (value > 10) {",
                "            return value;",
                "        } else {",
                "            throw new IllegalStateException();",
                "        }",
                "    }",
                "",
                "    public int choose(int value) {",
                "        switch (value) {",
                "            case 1:",
                "                return 42;",
                "            default:",
                "                return 0;",
                "        }",
                "    }",
                "}"));

        List<ScanEvent> events = new JavaAstScanner().scan(root, List.of(), List.of());
        assertFalse(events.isEmpty(), "Expected events to be emitted");

        assertTrue(events.stream()
                .filter(event -> "if-true".equals(event.kind()))
                .anyMatch(event -> "value > 10".equals(event.conditionText())
                        && "com.example.Sample".equals(event.fqcn())));
        assertTrue(events.stream()
                .filter(event -> "if-false".equals(event.kind()))
                .anyMatch(event -> "value > 10".equals(event.conditionText())));
        assertTrue(events.stream()
                .filter(event -> "switch".equals(event.kind()))
                .anyMatch(event -> "value".equals(event.conditionText())));
        assertTrue(events.stream()
                .filter(event -> "switch-case".equals(event.kind()))
                .anyMatch(event -> "case 1".equals(event.conditionText())));
        assertTrue(events.stream()
                .filter(event -> "return".equals(event.kind()))
                .anyMatch(event -> event.conditionText() == null));
        assertTrue(events.stream()
                .anyMatch(event -> "throw".equals(event.kind())));
    }
}
