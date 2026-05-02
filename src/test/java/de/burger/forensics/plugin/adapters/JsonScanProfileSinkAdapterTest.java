package de.burger.forensics.plugin.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.burger.forensics.domain.model.cache.ScanPhase;
import de.burger.forensics.domain.model.cache.ScanProfile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonScanProfileSinkAdapterTest {

    @Test
    void writesProfileWithoutPhaseDurations(@TempDir Path tempDir) throws Exception {
        Path report = tempDir.resolve("profiles/scan-profile.json");
        JsonScanProfileSinkAdapter sink = new JsonScanProfileSinkAdapter(report);

        sink.publish(ScanProfile.empty());

        String json = Files.readString(report);
        assertThat(json).contains("\"totalFiles\": 0");
        assertThat(json).contains("\"phaseDurationsNanos\": {\n  }");
    }

    @Test
    void writesSortedPhaseDurations(@TempDir Path tempDir) throws Exception {
        Path report = tempDir.resolve("scan-profile.json");
        JsonScanProfileSinkAdapter sink = new JsonScanProfileSinkAdapter(report);
        ScanProfile profile = new ScanProfile(
                Map.of(
                        ScanPhase.RULE_RENDERING, Duration.ofNanos(20),
                        ScanPhase.CACHE_READ, Duration.ofNanos(10)),
                1,
                1,
                0,
                1,
                0,
                1,
                2,
                3);

        sink.publish(profile);

        String json = Files.readString(report);
        assertThat(json).contains("\"CACHE_READ\": 10");
        assertThat(json).contains("\"RULE_RENDERING\": 20");
        assertThat(json.indexOf("\"CACHE_READ\"")).isLessThan(json.indexOf("\"RULE_RENDERING\""));
    }

    @Test
    void wrapsIoFailures(@TempDir Path tempDir) throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("profile-as-directory.json"));
        JsonScanProfileSinkAdapter sink = new JsonScanProfileSinkAdapter(directory);

        assertThatThrownBy(() -> sink.publish(ScanProfile.empty()))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to write scan profile report");
    }
}
