package de.burger.forensics.plugin.scan

import de.burger.forensics.plugin.scan.java.JavaAstScanner
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaAstScannerTest {

    @Test
    fun `emits events for control flow constructs`() {
        val root = Files.createTempDirectory("java-ast")
        val file = root.resolve("Sample.java")
        Files.writeString(
            file,
            """
            package com.example;

            public class Sample {
                public int compute(int value) {
                    if (value > 10) {
                        return value;
                    } else {
                        throw new IllegalStateException();
                    }
                }

                public int choose(int value) {
                    switch (value) {
                        case 1:
                            return 42;
                        default:
                            return 0;
                    }
                }
            }
            """.trimIndent()
        )

        val events = JavaAstScanner().scan(root, emptyList(), emptyList())
        assertThat(events).isNotEmpty
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("if-true")
            assertThat(event.conditionText).isEqualTo("value > 10")
            assertThat(event.fqcn).isEqualTo("com.example.Sample")
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("if-false")
            assertThat(event.conditionText).isEqualTo("value > 10")
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("switch")
            assertThat(event.conditionText).isEqualTo("value")
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("switch-case")
            assertThat(event.conditionText).isEqualTo("case 1")
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("return")
            assertThat(event.conditionText).isNull()
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("throw")
        }
    }
}
