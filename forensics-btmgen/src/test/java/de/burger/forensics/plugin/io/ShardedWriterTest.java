package de.burger.forensics.plugin.io;

import de.burger.forensics.plugin.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ShardedWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesPlainTextShards() throws Exception {
        File dir = tempDir.resolve("plain").toFile();
        ShardedWriter writer = new ShardedWriter(dir, 3, false, "tracing-");
        writer.writeHeader("# Header\n");
        for (int i = 0; i < 1_000; i++) {
            String key = "com.example.Plain#method:" + i;
            int shard = HashUtil.stableShard(key, 3);
            String rule = "RULE plain" + i + "\n" +
                "CLASS com.example.Plain\n" +
                "METHOD method(..)\n" +
                "AT ENTRY\n" +
                "DO traceln(\"ok\")\n" +
                "ENDRULE";
            writer.append(shard, rule);
        }
        writer.close();
        writer.close();

        List<Path> files = Files.list(dir.toPath()).sorted().toList();
        assertThat(files).hasSize(3);
        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(content).startsWith("# Header\n");
            assertThat(content).contains("RULE plain");
            assertThat(content).contains("\n\n");
        }
    }

    @Test
    void writesGzipShards() throws Exception {
        File dir = tempDir.resolve("gzip").toFile();
        try (ShardedWriter writer = new ShardedWriter(dir, 2, true, "tracing-")) {
            writer.writeHeader("# Header\n");
            writer.append(0, "RULE gzip\nCLASS Example\nMETHOD run(..)\nAT ENTRY\nDO traceln(\"x\")\nENDRULE");
        }

        List<Path> files = Files.list(dir.toPath()).sorted().toList();
        assertThat(files).hasSize(2);
        assertThat(files.get(0).getFileName().toString()).endsWith(".btm.gz");
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(files.get(0)))) {
            byte[] data = in.readAllBytes();
            String text = new String(data, StandardCharsets.UTF_8);
            assertThat(text).contains("RULE gzip");
        }
    }
}
