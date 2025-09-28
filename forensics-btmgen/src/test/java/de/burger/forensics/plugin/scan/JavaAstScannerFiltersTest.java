// DEST: src/test/java/de/burger/forensics/plugin/scan/JavaAstScannerFiltersTest.java
package de.burger.forensics.plugin.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.burger.forensics.plugin.scan.java.JavaAstScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class JavaAstScannerFiltersTest {

    @Test
    void includesAllWhenIncludeListIsEmptyAndExcludesNoneWhenExcludeListIsEmpty() throws IOException {
        Path root = Files.createTempDirectory("java-ast-inc-exc");
        Path keep = root.resolve("Keep.java");
        Path other = root.resolve("Other.java");
        Files.writeString(keep, String.join("\n",
                "package com.test.keep;",
                "public class Keep { public void m(){ if (true) { } } }"));
        Files.writeString(other, String.join("\n",
                "package com.test.other;",
                "public class Other { public void m(){ if (true) { } } }"));

        List<ScanEvent> events = new JavaAstScanner().scan(root, List.of(), List.of());
        assertFalse(events.isEmpty(), "Expected events to be emitted");
        Set<String> fqcnSet = events.stream().map(ScanEvent::fqcn).collect(Collectors.toSet());
        assertTrue(fqcnSet.contains("com.test.keep.Keep"));
        assertTrue(fqcnSet.contains("com.test.other.Other"));
    }

    @Test
    void appliesIncludeAndExcludePackagesWithPrefixSemantics() throws IOException {
        Path root = Files.createTempDirectory("java-ast-filtered");
        Path incOk = root.resolve("A.java");
        Path incExcluded = root.resolve("B.java");
        Path notIncluded = root.resolve("C.java");
        Files.writeString(incOk, String.join("\n",
                "package com.keep;",
                "public class A { public void a(){ if (true) {} } }"));
        Files.writeString(incExcluded, String.join("\n",
                "package com.keep.excluded.sub;",
                "public class B { public void b(){ if (true) {} } }"));
        Files.writeString(notIncluded, String.join("\n",
                "package com.drop;",
                "public class C { public void c(){ if (true) {} } }"));

        List<String> includePkgs = List.of("com.keep");
        List<String> excludePkgs = List.of("com.keep.excluded");

        List<ScanEvent> events = new JavaAstScanner().scan(root, includePkgs, excludePkgs);
        Set<String> fqcnSet = events.stream().map(ScanEvent::fqcn).collect(Collectors.toSet());

        assertTrue(fqcnSet.contains("com.keep.A"));
        assertFalse(fqcnSet.contains("com.keep.excluded.sub.B"));
        assertFalse(fqcnSet.contains("com.drop.C"));
    }

    @Test
    void resolvesNestedTypeNamesAndDefaultSwitchCaseLabel() throws IOException {
        Path root = Files.createTempDirectory("java-ast-nested");
        Path file = root.resolve("Outer.java");
        Files.writeString(file, String.join("\n",
                "package com.example;",
                "public class Outer {",
                "    public class Inner {",
                "        public int m(int v) {",
                "            switch (v) { default: return 1; }",
                "        }",
                "    }",
                "}");

        List<ScanEvent> events = new JavaAstScanner().scan(root, List.of(), List.of());
        assertFalse(events.isEmpty(), "Expected events to be emitted");
        Set<String> fqcnSet = events.stream().map(ScanEvent::fqcn).collect(Collectors.toSet());
        assertTrue(fqcnSet.contains("com.example.Outer$Inner"));

        assertTrue(events.stream()
                .filter(event -> "switch".equals(event.kind()))
                .anyMatch(event -> "v".equals(event.conditionText())));
        assertTrue(events.stream()
                .filter(event -> "switch-case".equals(event.kind()))
                .anyMatch(event -> "default".equals(event.conditionText())));
    }
}
