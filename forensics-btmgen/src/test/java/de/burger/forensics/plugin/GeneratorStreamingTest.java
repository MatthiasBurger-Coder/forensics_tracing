package de.burger.forensics.plugin;

import de.burger.forensics.plugin.io.ShardedWriter;
import de.burger.forensics.plugin.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorStreamingTest {

    @TempDir
    Path tempDir;

    @Test
    void streamingProducesDeterministicShards() throws Exception {
        List<String> rules = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String className = "com.example.C" + i;
            String methodName = "run" + (i % 4);
            int line = 10 + i;
            String rule = "RULE " + className + "." + methodName + ":" + line + ":if-true\n" +
                "CLASS " + className + "\n" +
                "METHOD " + methodName + "(..)\n" +
                "HELPER helper.Helper\n" +
                "AT LINE " + line + "\n" +
                "DO traceln(\"" + i + "\")\n" +
                "ENDRULE";
            rules.add(rule);
        }

        List<String> first = writeSharded(tempDir.resolve("first").toFile(), rules, 4);
        long totalRules = first.stream()
            .flatMap(content -> content.lines())
            .filter(line -> line.startsWith("RULE "))
            .count();
        assertThat(totalRules).isEqualTo(20);

        List<String> second = writeSharded(tempDir.resolve("second").toFile(), rules, 4);
        assertThat(second).containsExactlyElementsOf(first);
    }

    private List<String> writeSharded(File dir, List<String> rules, int shards) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory " + dir);
        }
        ShardedWriter writer = new ShardedWriter(dir, shards, false, "tracing-");
        writer.writeHeader("# Header\n");
        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i);
            String className = "com.example.C" + i;
            String methodName = "run" + (i % 4);
            int line = 10 + i;
            String key = className + "#" + methodName + ":" + line;
            int shard = HashUtil.stableShard(key, shards);
            writer.append(shard, rule);
        }
        writer.close();
        writer.close();
        try (var stream = Files.list(dir.toPath())) {
            return stream
                .sorted(Comparator.comparing(Path::getFileName))
                .map(path -> {
                    try {
                        return Files.readString(path, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
        }
    }
}
