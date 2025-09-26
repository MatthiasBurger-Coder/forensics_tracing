package de.burger.forensics.plugin.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileSizeGuardTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `skips when file exceeds limit`() {
        val limit = 1_024L
        val file = Files.createTempFile(tempDir, "big", ".java").toFile()
        file.writeBytes(ByteArray((limit + 10).toInt()) { 'A'.code.toByte() })

        val messages = mutableListOf<String>()
        val skipped = shouldSkipLargeFile(file, limit) { messages += it }

        assertThat(skipped).isTrue()
        assertThat(messages).isNotEmpty()
        assertThat(messages.first()).contains("Skipping large file")
    }

    @Test
    fun `processes when file size is within limit`() {
        val limit = 1_024L
        val file = Files.createTempFile(tempDir, "small", ".java").toFile()
        file.writeBytes(ByteArray(limit.toInt()) { 'B'.code.toByte() })

        val messages = mutableListOf<String>()
        val skipped = shouldSkipLargeFile(file, limit) { messages += it }

        assertThat(skipped).isFalse()
        assertThat(messages).isEmpty()
    }
}
