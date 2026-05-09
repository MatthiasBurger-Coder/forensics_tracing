package de.burger.forensics.adapters.filesystem;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactChecksumServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void computesFileChecksumWithRelativePath() throws IOException {
        Path artifact = tempDir.resolve("forensics.btm");
        Files.writeString(artifact, "rules");

        ArtifactChecksum checksum = new ArtifactChecksumService()
                .checksumFile(tempDir, artifact, "byteman-rules");

        assertThat(checksum.path()).isEqualTo("forensics.btm");
        assertThat(checksum.type()).isEqualTo("byteman-rules");
        assertThat(checksum.sha256()).hasSize(64);
        assertThat(checksum.sizeBytes()).isEqualTo(5L);
    }

    @Test
    void computesDeterministicDirectoryChecksumAndFileEntries() throws IOException {
        Path store = tempDir.resolve("analysis-store");
        Files.createDirectories(store);
        Files.writeString(store.resolve("b.mv.db"), "b");
        Files.writeString(store.resolve("a.trace.db"), "a");

        ArtifactChecksumService service = new ArtifactChecksumService();

        ArtifactChecksum first = service.checksumDirectory(tempDir, store, "h2-analysis-store");
        ArtifactChecksum second = service.checksumDirectory(tempDir, store, "h2-analysis-store");
        List<ArtifactChecksum> files = service.checksumFiles(tempDir, store, "h2-analysis-store-file");

        assertThat(first).isEqualTo(second);
        assertThat(first.path()).isEqualTo("analysis-store");
        assertThat(files).extracting(ArtifactChecksum::path)
                .containsExactly("analysis-store/a.trace.db", "analysis-store/b.mv.db");
    }

    @Test
    void computesSingleFileEntriesWhenArtifactRootIsAFile() throws IOException {
        Path artifact = tempDir.resolve("forensics.btm");
        Files.writeString(artifact, "rules");

        List<ArtifactChecksum> checksums = new ArtifactChecksumService()
                .checksumFiles(tempDir, artifact, "byteman-rules");

        assertThat(checksums).singleElement()
                .extracting(ArtifactChecksum::path)
                .isEqualTo("forensics.btm");
    }

    @Test
    void returnsEmptyFileEntriesForMissingArtifactRoots() {
        List<ArtifactChecksum> checksums = new ArtifactChecksumService()
                .checksumFiles(tempDir, tempDir.resolve("missing"), "h2-analysis-store-file");

        assertThat(checksums).isEmpty();
    }

    @Test
    void usesFileNameWhenArtifactIsOutsideBaseDirectory() throws IOException {
        Path otherBase = Files.createTempDirectory("artifact-checksum-outside");
        Path artifact = otherBase.resolve("external.bin");
        Files.writeString(artifact, "external");

        ArtifactChecksum checksum = new ArtifactChecksumService()
                .checksumFile(tempDir, artifact, "external-artifact");

        assertThat(checksum.path()).isEqualTo("external.bin");
    }
}
