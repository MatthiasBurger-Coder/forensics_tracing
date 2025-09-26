package de.burger.forensics.plugin.io;

import de.burger.forensics.plugin.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ShardedWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesPlainTextShards() throws Exception {
        File dir = tempDir.resolve("plain").toFile();
        try (ShardedWriter writer = new ShardedWriter(dir, 3, false, "tracing-")) {
            writer.writeHeader("# Header\n");
            for (int i = 0; i < 1_000; i++) {
                String key = "com.example.Plain#method:" + i;
                int shard = HashUtil.stableShard(key, 3);
                writer.append(shard, buildRule("plain" + i, "ok"));
            }
        }

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
            writer.append(0, buildRule("gzip", "x"));
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

    @Test
    void rotatesWhenMaxBytesExceeded() throws Exception {
        File dir = tempDir.resolve("rotate-size").toFile();
        try (ShardedWriter writer = new ShardedWriter(dir, 1, false, "tracing-", 512, 0, 64, 0, false)) {
            writer.writeHeader("# Header\n");
            for (int i = 0; i < 20; i++) {
                writer.append(0, buildRule("size-" + i, "payload" + i));
            }
        }

        List<Path> files = Files.list(dir.toPath()).sorted().toList();
        assertThat(files.size()).isGreaterThan(1);
        assertThat(files.get(0).getFileName().toString()).matches("tracing-0001-\\d{5}\\.btm");

        Pattern pattern = Pattern.compile("RULE size-(\\d+)");
        List<Integer> ids = new ArrayList<>();
        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                ids.add(Integer.parseInt(matcher.group(1)));
            }
        }
        assertThat(ids).hasSize(20);
        for (int i = 0; i < ids.size(); i++) {
            assertThat(ids.get(i)).isEqualTo(i);
        }
    }

    @Test
    void rotatesWhenIntervalElapsed() throws Exception {
        File dir = tempDir.resolve("rotate-time").toFile();
        try (ShardedWriter writer = new ShardedWriter(dir, 1, false, "tracing-", 0, 1, 0, 0, false)) {
            writer.writeHeader("# Header\n");
            writer.append(0, buildRule("time-0", "first"));
            TimeUnit.MILLISECONDS.sleep(1100);
            writer.append(0, buildRule("time-1", "second"));
        }

        List<Path> files = Files.list(dir.toPath()).sorted().toList();
        assertThat(files.size()).isGreaterThanOrEqualTo(2);
        for (Path file : files) {
            assertThat(file.getFileName().toString()).matches("tracing-0001-\\d{5}\\.btm");
        }
    }

    @Test
    void flushesAfterThresholdExceeded() throws Exception {
        File dir = tempDir.resolve("flush-threshold").toFile();
        try (ShardedWriter writer = new ShardedWriter(dir, 1, false, "tracing-", 0, 0, 32, 0, false)) {
            writer.writeHeader("");
            writer.append(0, buildRule("flush-threshold", "x".repeat(80)));
            Path file = dir.toPath().resolve("tracing-0001.btm");
            assertThat(Files.size(file)).isGreaterThan(0L);
        }
    }

    @Test
    void flushesAfterIntervalElapsed() throws Exception {
        File dir = tempDir.resolve("flush-interval").toFile();
        try (ShardedWriter writer = new ShardedWriter(dir, 1, false, "tracing-", 0, 0, 1024, 100, false)) {
            writer.writeHeader("");
            writer.append(0, buildRule("flush-interval-0", "short"));
            Path file = dir.toPath().resolve("tracing-0001.btm");
            long sizeAfterFirst = Files.size(file);
            TimeUnit.MILLISECONDS.sleep(150);
            writer.append(0, buildRule("flush-interval-1", "again"));
            long sizeAfterSecond = Files.size(file);
            assertThat(sizeAfterSecond).isGreaterThan(sizeAfterFirst);
        }
    }

    @Test
    void concurrentAppendsAreSafe() throws Exception {
        File dir = tempDir.resolve("thread-safe").toFile();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ShardedWriter writer = new ShardedWriter(dir, 1, false, "tracing-", 0, 0, 0, 0, true)) {
            writer.writeHeader("# Header\n");
            Runnable task = () -> {
                for (int i = 0; i < 200; i++) {
                    try {
                        writer.append(0, buildRule("thread-" + Thread.currentThread().getId() + "-" + i, "data" + i));
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                        throw new RuntimeException(t);
                    }
                }
            };
            Thread first = new Thread(task);
            Thread second = new Thread(task);
            Thread third = new Thread(task);
            first.start();
            second.start();
            third.start();
            first.join();
            second.join();
            third.join();
        }

        if (failure.get() != null) {
            throw new AssertionError("Concurrent append failed", failure.get());
        }

        Path file = dir.toPath().resolve("tracing-0001.btm");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        long ruleCount = text.lines().filter(line -> line.equals("ENDRULE")).count();
        assertThat(ruleCount).isEqualTo(600);
        assertThat(text).contains("RULE thread-");
    }

    @Test
    void keepsLegacyNamesWhenRotationDisabled() throws Exception {
        File dir = tempDir.resolve("legacy-names").toFile();
        try (ShardedWriter writer = new ShardedWriter(dir, 2, false, "legacy-", 0, 0, 0, 0, false)) {
            writer.writeHeader("# Header\n");
            writer.append(0, buildRule("legacy-0", "first"));
            writer.append(1, buildRule("legacy-1", "second"));
        }

        List<String> names = Files.list(dir.toPath())
            .map(path -> path.getFileName().toString())
            .sorted()
            .toList();
        assertThat(names).containsExactly("legacy-0001.btm", "legacy-0002.btm");
    }

    private String buildRule(String name, String message) {
        return "RULE " + name + "\n" +
            "CLASS com.example.Sample\n" +
            "METHOD probe(..)\n" +
            "AT ENTRY\n" +
            "DO traceln(\"" + message + "\")\n" +
            "ENDRULE";
    }
}
