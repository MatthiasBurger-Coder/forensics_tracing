package de.burger.forensics.plugin.scan

import de.burger.forensics.plugin.scan.kotlin.KotlinAstScanner
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinAstScannerTest {

    @Test
    fun `captures Kotlin branches`() {
        val root = Files.createTempDirectory("kotlin-ast")
        val file = root.resolve("Demo.kt")
        Files.writeString(
            file,
            """
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
            }
            """.trimIndent()
        )

        val events = KotlinAstScanner().scan(root, emptyList(), emptyList())
        assertThat(events).isNotEmpty
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("if-true")
            assertThat(event.conditionText).isEqualTo("value > 5")
            assertThat(event.fqcn).isEqualTo("com.example.Demo")
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("switch")
            assertThat(event.conditionText).isEqualTo("flag")
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("when-branch")
            assertThat(event.conditionText).isEqualTo("1")
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("return")
        }
        assertThat(events).anySatisfy { event ->
            assertThat(event.kind).isEqualTo("throw")
        }
    }
}
