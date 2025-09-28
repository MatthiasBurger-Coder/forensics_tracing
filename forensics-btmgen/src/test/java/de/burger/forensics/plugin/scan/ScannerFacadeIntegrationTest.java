// DEST: src/test/java/de/burger/forensics/plugin/scan/ScannerFacadeIntegrationTest.java
package de.burger.forensics.plugin.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScannerFacadeIntegrationTest {

    @Test
    void scansJavaSources() throws IOException {
        Path root = Files.createTempDirectory("facade-ast");
        Path javaFile = root.resolve("Joint.java");
        Files.writeString(javaFile, String.join("\n",
                "package com.example;",
                "",
                "public class Joint {",
                "    public void run(boolean ok) {",
                "        if (ok) {",
                "            System.out.println(\"OK\");",
                "        }",
                "    }",
                "    public int pick(int value) {",
                "        if (value > 0) {",
                "            return 1;",
                "        }",
                "        return 0;",
                "    }",
                "}"));

        List<ScanEvent> events = new ScannerFacade().scan(root, List.of(), List.of());
        assertFalse(events.isEmpty(), "Expected events to be emitted");
        Set<String> languages = events.stream().map(ScanEvent::language).collect(Collectors.toSet());
        assertEquals(Set.of("java"), languages);
        assertTrue(events.stream().anyMatch(event -> "java".equals(event.language()) && "if-true".equals(event.kind())));
    }
}
