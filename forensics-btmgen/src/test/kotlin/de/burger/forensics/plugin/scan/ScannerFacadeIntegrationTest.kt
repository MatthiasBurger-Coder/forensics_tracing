package de.burger.forensics.plugin.scan

import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScannerFacadeIntegrationTest {

    @Test
    fun `scans Java and Kotlin sources together`() {
        val root = Files.createTempDirectory("facade-ast")
        val javaFile = root.resolve("Joint.java")
        Files.writeString(
            javaFile,
            """
            package com.example;

            public class Joint {
                public void run(boolean ok) {
                    if (ok) {
                        System.out.println("OK");
                    }
                }
            }
            """.trimIndent()
        )
        val kotlinFile = root.resolve("Joint.kt")
        Files.writeString(
            kotlinFile,
            """
            package com.example

            class JointKt {
                fun test(value: Int) = if (value > 0) "p" else "n"
            }
            """.trimIndent()
        )

        val events = ScannerFacade().scan(root, emptyList(), emptyList())
        assertThat(events).hasSizeGreaterThan(0)
        assertThat(events.map { it.language }.toSet()).containsExactlyInAnyOrder("java", "kotlin")
        assertThat(events).anyMatch { it.language == "java" && it.kind == "if-true" }
        assertThat(events).anyMatch { it.language == "kotlin" && it.kind == "if-true" }
    }
}
