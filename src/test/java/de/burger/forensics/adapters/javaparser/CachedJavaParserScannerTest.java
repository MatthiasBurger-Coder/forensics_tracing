package de.burger.forensics.adapters.javaparser;

import de.burger.forensics.adaptersupport.javaparser.DefaultSourceFingerprintPort;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.cache.CachedScanResult;
import de.burger.forensics.domain.model.cache.ScanDependency;
import de.burger.forensics.domain.model.cache.ScanProfile;
import de.burger.forensics.domain.model.cache.SourceFileFingerprint;
import de.burger.forensics.domain.model.cache.SourceFileSnapshot;
import de.burger.forensics.domain.port.out.ScanCachePort;
import de.burger.forensics.domain.port.out.ScanProfileSinkPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachedJavaParserScannerTest {

    @Test
    void storesEventsAndDependenciesOnFirstScan(@TempDir Path tempDir) throws IOException {
        InMemoryScanCache cache = new InMemoryScanCache();
        CachedJavaParserScanner scanner = scanner(cache);
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, sourceWithBranch("Sample", "dependency.call();"));

        List<ScanEvent> events = scanner.scan(tempDir).toList();
        CachedScanResult stored = cache.singleResult();

        assertThat(events).hasSize(2);
        assertThat(stored.events()).isEqualTo(events);
        assertThat(stored.dependencies()).extracting(ScanDependency::target)
                .contains("dependency.call");
        assertThat(stored.source().parseSucceeded()).isTrue();
        assertThat(cache.storeCount()).isEqualTo(1);
    }

    @Test
    void loadsIdenticalFingerprintsFromCacheWithoutWritingAgain(@TempDir Path tempDir) throws IOException {
        InMemoryScanCache cache = new InMemoryScanCache();
        RecordingProfileSink profileSink = new RecordingProfileSink();
        CachedJavaParserScanner scanner = new CachedJavaParserScanner(
                cache,
                new DefaultSourceFingerprintPort(),
                profileSink,
                false);
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, sourceWithBranch("Sample", ""));

        List<ScanEvent> first = scanner.scan(tempDir).toList();
        List<ScanEvent> second = scanner.scan(tempDir).toList();

        assertThat(second).isEqualTo(first);
        assertThat(cache.storeCount()).isEqualTo(1);
        assertThat(profileSink.lastProfile().cacheHitFiles()).isEqualTo(1);
        assertThat(profileSink.lastProfile().parsedFiles()).isZero();
    }

    @Test
    void reparsesChangedFiles(@TempDir Path tempDir) throws IOException {
        InMemoryScanCache cache = new InMemoryScanCache();
        CachedJavaParserScanner scanner = scanner(cache);
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, sourceWithBranch("Sample", ""));
        scanner.scan(tempDir).toList();

        Files.writeString(source, """
            package example;
            class Sample {
                int run(int value) {
                    return value;
                }
            }
            """);
        List<ScanEvent> changedEvents = scanner.scan(tempDir).toList();

        assertThat(changedEvents).extracting(ScanEvent::kind)
                .containsExactly(RuleTemplate.RETURN);
        assertThat(cache.storeCount()).isEqualTo(2);
    }

    @Test
    void ignoresCacheEntryWhenCacheReturnsDifferentFingerprint(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, sourceWithBranch("Sample", ""));
        SourceFileSnapshot current = new DefaultSourceFingerprintPort().snapshot(tempDir, source);
        SourceFileSnapshot staleSource = new SourceFileSnapshot(
                current.rootPath(),
                current.relativePath(),
                current.sourcePath(),
                new SourceFileFingerprint("SHA-256", "stale-hash"),
                current.size(),
                current.lastModifiedAt(),
                true,
                Optional.empty());
        CachedScanResult staleResult = new CachedScanResult(
                staleSource,
                List.of(new ScanEvent(
                        new SourceLocation("example.Sample", "stale", 1),
                        "void stale()",
                        RuleTemplate.METHOD_ENTER,
                        "",
                        "java",
                        "void")),
                List.of(),
                ScanProfile.empty());
        StaleReturningScanCache cache = new StaleReturningScanCache(staleResult);
        CachedJavaParserScanner scanner = scanner(cache);

        List<ScanEvent> events = scanner.scan(tempDir).toList();

        assertThat(events).extracting(event -> event.location().method()).containsOnly("run");
        assertThat(cache.storeCount()).isEqualTo(1);
    }

    @Test
    void removesDeletedFilesFromCache(@TempDir Path tempDir) throws IOException {
        InMemoryScanCache cache = new InMemoryScanCache();
        CachedJavaParserScanner scanner = scanner(cache);
        Path kept = tempDir.resolve("Kept.java");
        Path deleted = tempDir.resolve("Deleted.java");
        Files.writeString(kept, sourceWithBranch("Kept", ""));
        Files.writeString(deleted, sourceWithBranch("Deleted", ""));
        scanner.scan(tempDir).toList();

        Files.delete(deleted);
        List<ScanEvent> events = scanner.scan(tempDir).toList();

        assertThat(events).hasSize(2);
        assertThat(cache.containsRelativePath("Kept.java")).isTrue();
        assertThat(cache.containsRelativePath("Deleted.java")).isFalse();
    }

    @Test
    void doesNotReuseStaleEventsWhenChangedFileFailsToParse(@TempDir Path tempDir) throws IOException {
        InMemoryScanCache cache = new InMemoryScanCache();
        CachedJavaParserScanner scanner = scanner(cache);
        Path source = tempDir.resolve("Sample.java");
        Files.writeString(source, sourceWithBranch("Sample", ""));
        scanner.scan(tempDir).toList();

        Files.writeString(source, "class {");
        List<ScanEvent> events = scanner.scan(tempDir).toList();
        CachedScanResult stored = cache.result("Sample.java").orElseThrow();

        assertThat(events).isEmpty();
        assertThat(stored.events()).isEmpty();
        assertThat(stored.source().parseSucceeded()).isFalse();
        assertThat(stored.source().failureMessage()).isPresent();
    }

    @Test
    void throwsForParseErrorsWhenStrictParsingIsEnabled(@TempDir Path tempDir) throws IOException {
        InMemoryScanCache cache = new InMemoryScanCache();
        CachedJavaParserScanner scanner = new CachedJavaParserScanner(
                cache,
                new DefaultSourceFingerprintPort(),
                null,
                true);
        Files.writeString(tempDir.resolve("Broken.java"), "class {");

        assertThatThrownBy(() -> scanAll(scanner, tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse Java source");
    }

    @Test
    void publishesScanProfileWhenSinkIsPresent(@TempDir Path tempDir) throws IOException {
        InMemoryScanCache cache = new InMemoryScanCache();
        RecordingProfileSink profileSink = new RecordingProfileSink();
        CachedJavaParserScanner scanner = new CachedJavaParserScanner(
                cache,
                new DefaultSourceFingerprintPort(),
                profileSink,
                false);
        Files.writeString(tempDir.resolve("Sample.java"), sourceWithBranch("Sample", ""));

        scanner.scan(tempDir).toList();

        ScanProfile profile = profileSink.lastProfile();
        assertThat(profile.totalFiles()).isEqualTo(1);
        assertThat(profile.parsedFiles()).isEqualTo(1);
        assertThat(profile.cacheMissFiles()).isEqualTo(1);
        assertThat(profile.totalEvents()).isEqualTo(2);
        assertThat(profile.phaseDurations()).isNotEmpty();
    }

    private CachedJavaParserScanner scanner(ScanCachePort cache) {
        return new CachedJavaParserScanner(cache, new DefaultSourceFingerprintPort());
    }

    private static void scanAll(CachedJavaParserScanner scanner, Path sourceRoot) {
        scanner.scan(sourceRoot).toList();
    }

    private static String sourceWithBranch(String className, String statement) {
        return """
            package example;
            class %s {
                private Dependency dependency;
                void run(int value) {
                    if (value > 0) {
                        %s
                    }
                }
            }
            """.formatted(className, statement);
    }

    private static final class RecordingProfileSink implements ScanProfileSinkPort {

        private final List<ScanProfile> profiles = new ArrayList<>();

        @Override
        public void publish(ScanProfile profile) {
            profiles.add(profile);
        }

        private ScanProfile lastProfile() {
            return profiles.get(profiles.size() - 1);
        }
    }

    private static final class InMemoryScanCache implements ScanCachePort {

        private final Map<String, CachedScanResult> results = new LinkedHashMap<>();
        private int storeCount;

        @Override
        public void initialize() {
            // In-memory test cache has no initialization state.
        }

        @Override
        public Optional<CachedScanResult> find(SourceFileSnapshot source) {
            return result(source.relativePath())
                    .filter(result -> result.source().fingerprint().equals(source.fingerprint()));
        }

        @Override
        public void store(CachedScanResult result) {
            storeCount++;
            results.put(key(result.source()), result);
        }

        @Override
        public void deleteMissing(String rootPath, Set<String> currentRelativePaths) {
            results.entrySet().removeIf(entry ->
                    entry.getValue().source().rootPath().toString().equals(rootPath)
                            && !currentRelativePaths.contains(entry.getValue().source().relativePath()));
        }

        @Override
        public void rebuild() {
            results.clear();
        }

        private int storeCount() {
            return storeCount;
        }

        private CachedScanResult singleResult() {
            assertThat(results).hasSize(1);
            return results.values().iterator().next();
        }

        private Optional<CachedScanResult> result(String relativePath) {
            return results.values().stream()
                    .filter(result -> result.source().relativePath().equals(relativePath))
                    .findFirst();
        }

        private boolean containsRelativePath(String relativePath) {
            return result(relativePath).isPresent();
        }

        private String key(SourceFileSnapshot source) {
            return source.rootPath() + "::" + source.relativePath();
        }
    }

    private static final class StaleReturningScanCache implements ScanCachePort {

        private final CachedScanResult staleResult;
        private int storeCount;

        private StaleReturningScanCache(CachedScanResult staleResult) {
            this.staleResult = staleResult;
        }

        @Override
        public void initialize() {
            // This test cache only returns a fixed stale entry.
        }

        @Override
        public Optional<CachedScanResult> find(SourceFileSnapshot source) {
            return Optional.of(staleResult);
        }

        @Override
        public void store(CachedScanResult result) {
            storeCount++;
        }

        @Override
        public void deleteMissing(String rootPath, Set<String> currentRelativePaths) {
            // Stale-entry tests do not exercise deletion behavior.
        }

        @Override
        public void rebuild() {
            // Stale-entry tests do not exercise rebuild behavior.
        }

        private int storeCount() {
            return storeCount;
        }
    }
}
