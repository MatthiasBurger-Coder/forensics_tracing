package de.burger.forensics.plugin.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BtmFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesTimestampHeaderWhenEnabled() throws IOException {
        Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:34:56Z"), ZoneOffset.UTC);
        BtmFileWriter writer = new BtmFileWriter(fixedClock);
        Path output = tempDir.resolve("logs/rules.btm");

        writer.write(output, true, "org.example.Helper", List.of("RULE one"));

        String content = Files.readString(output);
        assertThat(content)
            .contains("# Generated at 2024-01-01T12:34:56Z")
            .contains("# Helper: org.example.Helper")
            .contains("RULE one");
    }

    @Test
    void omitsTimestampWhenDisabled() throws IOException {
        Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:34:56Z"), ZoneOffset.UTC);
        BtmFileWriter writer = new BtmFileWriter(fixedClock);
        Path output = tempDir.resolve("rules.btm");

        writer.write(output, false, "helper", List.of());

        List<String> lines = Files.readAllLines(output);
        assertThat(lines.getFirst()).isEqualTo("# Generated rules");
    }

    @Test
    void createsMissingDirectories() throws IOException {
        Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:34:56Z"), ZoneOffset.UTC);
        BtmFileWriter writer = new BtmFileWriter(fixedClock);
        Path output = tempDir.resolve("nested/path/rules.btm");

        writer.write(output, true, "helper", List.of("RULE two"));

        assertThat(Files.exists(output)).isTrue();
    }
}
