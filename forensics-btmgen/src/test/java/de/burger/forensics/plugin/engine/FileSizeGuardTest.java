// DEST: src/test/java/de/burger/forensics/plugin/engine/FileSizeGuardTest.java
package de.burger.forensics.plugin.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSizeGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void skipsWhenFileExceedsLimit() throws IOException {
        long limit = 1_024L;
        Path file = Files.createTempFile(tempDir, "big", ".java");
        byte[] payload = new byte[(int) (limit + 10)];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = 'A';
        }
        Files.write(file, payload);

        List<String> messages = new ArrayList<>();
        boolean skipped = SourceFileGuards.shouldSkipLargeFile(file.toFile(), limit, messages::add);

        assertTrue(skipped, "File larger than limit should be skipped");
        assertFalse(messages.isEmpty(), "Expected diagnostic messages");
        assertTrue(messages.get(0).contains("Skipping large file"));
    }

    @Test
    void processesWhenFileSizeIsWithinLimit() throws IOException {
        long limit = 1_024L;
        Path file = Files.createTempFile(tempDir, "small", ".java");
        byte[] payload = new byte[(int) limit];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = 'B';
        }
        Files.write(file, payload);

        List<String> messages = new ArrayList<>();
        boolean skipped = SourceFileGuards.shouldSkipLargeFile(file.toFile(), limit, messages::add);

        assertFalse(skipped, "File within limit should not be skipped");
        assertTrue(messages.isEmpty(), "No diagnostic messages expected");
    }
}
