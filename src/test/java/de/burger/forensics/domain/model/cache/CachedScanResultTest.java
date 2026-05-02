package de.burger.forensics.domain.model.cache;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CachedScanResultTest {

    @Test
    void preservesScanEventSnapshotAcrossRecordRoundtrip() {
        CachedScanResult result = new CachedScanResult(
                new SourceFileSnapshot(
                        Path.of("src/main/java"),
                        "Sample.java",
                        Path.of("/workspace/src/main/java/Sample.java"),
                        new SourceFileFingerprint("SHA-256", "source-hash"),
                        128L,
                        Instant.parse("2026-05-02T10:15:30Z"),
                        true,
                        Optional.empty()),
                List.of(new ScanEvent(
                        new SourceLocation("sample.Sample", "run", 42),
                        "void run()",
                        RuleTemplate.METHOD_ENTER,
                        "",
                        "java",
                        "void")),
                List.of(new ScanDependency(
                        DependencyKind.METHOD_CALL,
                        "Sample.java",
                        "sample.Sample",
                        "run",
                        "sample.Dependency.call",
                        42,
                        17)),
                new ScanProfile(Map.of(ScanPhase.JAVA_PARSER_PARSE, Duration.ofMillis(12)),
                        1, 1, 0, 1, 0, 1, 1, 1));

        CachedScanResult roundtrip = new CachedScanResult(
                result.source(),
                result.events(),
                result.dependencies(),
                result.profile());

        assertThat(roundtrip).isEqualTo(result);
    }
}
