package de.burger.forensics.plugin.scan;

import de.burger.forensics.plugin.scan.kotlin.KotlinAstScanner;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// English comments only in code.
// Java migration of the former KotlinAstScannerTest. Verifies the Java KotlinAstScanner
// produces basic events for Kotlin sources without Kotlin PSI.
public class KotlinAstScannerTest {

    @Test
    void capturesKotlinBranchesAndStatements() throws IOException {
        Path root = Files.createTempDirectory("kotlin-ast");
        Path file = root.resolve("Demo.kt");
        Files.writeString(file, """
            package com.example

            class Demo {
                fun check(value: Int): Int {
                    if (value > 5) {
                        return value
                    } else {
                        throw IllegalArgumentException()
                    }
                }

                fun render(flag: Int): String {
                    return when (flag) {
                        1 -> "one"
                        else -> "other"
                    }
                }

                fun multiLineIf(x: Int): Int {
                    if (
                        x > 0 &&
                        x < 10
                    ) {
                        return x
                    }
                    return -1
                }

                fun multiLineWhen(text: String): String {
                    return when (
                        text
                            .trim()
                            .lowercase()
                    ) {
                        "yes" -> "y"
                        else -> "n"
                    }
                }
            }
            """.stripIndent());

        List<ScanEvent> events = new KotlinAstScanner().scan(root, List.of(), List.of());
        Assertions.assertThat(events).isNotEmpty();
        Assertions.assertThat(events).anySatisfy(e -> {
            Assertions.assertThat(e.kind()).isEqualTo("if-true");
            Assertions.assertThat(e.conditionText()).isEqualTo("value > 5");
            Assertions.assertThat(e.fqcn()).isEqualTo("com.example.Demo");
        });
        Assertions.assertThat(events).anySatisfy(e -> {
            Assertions.assertThat(e.kind()).isEqualTo("switch");
            Assertions.assertThat(e.conditionText()).isEqualTo("flag");
        });
        Assertions.assertThat(events).anySatisfy(e -> {
            Assertions.assertThat(e.kind()).isEqualTo("if-true");
            Assertions.assertThat(e.conditionText()).isEqualTo("x > 0 && x < 10");
        });
        Assertions.assertThat(events).anySatisfy(e -> {
            Assertions.assertThat(e.kind()).isEqualTo("switch");
            Assertions.assertThat(e.conditionText()).isEqualTo("text.trim().lowercase()");
        });
        Assertions.assertThat(events).anySatisfy(e -> Assertions.assertThat(e.kind()).isEqualTo("when-branch"));
        Assertions.assertThat(events).anySatisfy(e -> {
            Assertions.assertThat(e.kind()).isEqualTo("return");
            Assertions.assertThat(e.conditionText()).isNull();
        });
        Assertions.assertThat(events).anySatisfy(e -> Assertions.assertThat(e.kind()).isEqualTo("throw"));
    }
}
