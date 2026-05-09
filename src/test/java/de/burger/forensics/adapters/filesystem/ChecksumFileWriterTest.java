package de.burger.forensics.adapters.filesystem;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChecksumFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesSha256SumCompatibleSortedLines() throws IOException {
        Path checksums = tempDir.resolve("checksums.sha256");

        new ChecksumFileWriter().write(checksums, List.of(
                new ArtifactChecksum("z.bin", "file", "zzz", 1L),
                new ArtifactChecksum("a.bin", "file", "aaa", 1L)));

        assertThat(Files.readAllLines(checksums))
                .containsExactly("aaa  a.bin", "zzz  z.bin");
    }

    @Test
    void wrapsChecksumWriteFailures() {
        Path checksums = tempDir.resolve("checksums-dir");

        assertThatCode(() -> Files.createDirectories(checksums))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> new ChecksumFileWriter().write(checksums, List.of()))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("Failed to write checksums file");
    }
}
