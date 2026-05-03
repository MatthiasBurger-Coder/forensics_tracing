package de.burger.forensics.plugin.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.burger.forensics.domain.model.ConditionResolutionStatus;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.SourceContext;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.cache.ScanPhase;
import de.burger.forensics.domain.model.cache.ScanProfile;
import de.burger.forensics.domain.validation.ConditionValidationIssue;
import de.burger.forensics.domain.validation.ConditionValidationReport;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
        assertThat(json)
                .contains("\"totalFiles\": 0")
                .contains("\"phaseDurationsNanos\": {\n  }");
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
        assertThat(json)
                .contains("\"CACHE_READ\": 10")
                .contains("\"RULE_RENDERING\": 20");
        assertThat(json.indexOf("\"CACHE_READ\"")).isLessThan(json.indexOf("\"RULE_RENDERING\""));
    }

    @Test
    void writesConditionValidationIssues(@TempDir Path tempDir) throws Exception {
        Path report = tempDir.resolve("scan-profile.json");
        JsonScanProfileSinkAdapter sink = new JsonScanProfileSinkAdapter(report);
        ConditionValidationReport validationReport = new ConditionValidationReport(List.of(
                new ConditionValidationIssue(
                        new SourceLocation("com.example.Sample", "run", 42),
                        "DeploymentType.EAR != null",
                        "DeploymentType",
                        RuleTemplate.IF_TRUE)
        ));

        sink.publish(ScanProfile.empty(), validationReport);

        String json = Files.readString(report);
        assertThat(json)
                .contains("\"conditionValidation\"")
                .contains("\"issueCount\": 1")
                .contains("\"uniqueSymbolCount\": 1")
                .contains("\"symbol\":\"DeploymentType\"")
                .contains("\"className\":\"com.example.Sample\"")
                .contains("\"methodName\":\"run\"")
                .contains("\"line\":42")
                .contains("\"template\":\"IF_TRUE\"")
                .contains("\"expressionPreview\":\"DeploymentType.EAR != null\"");
    }

    @Test
    void writesConditionValidationGroups(@TempDir Path tempDir) throws Exception {
        Path report = tempDir.resolve("scan-profile.json");
        JsonScanProfileSinkAdapter sink = new JsonScanProfileSinkAdapter(report);
        SourceContext sourceContext = new SourceContext(
                "com.example.deployment",
                "src/main/java/com/example/deployment/DeploymentProcessor.java",
                "com.example.deployment.DeploymentProcessor",
                "DeploymentProcessor",
                "deploy",
                "deploy()");
        ConditionValidationReport validationReport = new ConditionValidationReport(List.of(
                new ConditionValidationIssue(
                        new SourceLocation("com.example.deployment.DeploymentProcessor", "deploy", 42),
                        "DeploymentType.EAR != null",
                        "DeploymentType",
                        RuleTemplate.IF_TRUE,
                        ConditionResolutionStatus.UNRESOLVED,
                        "Test diagnostic.",
                        sourceContext),
                new ConditionValidationIssue(
                        new SourceLocation("com.example.deployment.DeploymentProcessor", "deploy", 43),
                        "DeploymentType.WAR != null",
                        "DeploymentType",
                        RuleTemplate.IF_TRUE,
                        ConditionResolutionStatus.UNRESOLVED,
                        "Test diagnostic.",
                        sourceContext)
        ));

        sink.publish(ScanProfile.empty(), validationReport);

        String json = Files.readString(report);
        assertThat(json)
                .contains("\"groups\"")
                .contains("\"symbol\":\"DeploymentType\"")
                .contains("\"totalOccurrences\": 2")
                .contains("\"packageName\":\"com.example.deployment\"")
                .contains("\"className\":\"DeploymentProcessor\"")
                .contains("\"methodName\":\"deploy\"")
                .contains("\"locations\"")
                .contains("\"sourceFilePath\":\"src/main/java/com/example/deployment/DeploymentProcessor.java\"");
    }

    @Test
    void writesLegacyValidationIssuesWithoutTemplate(@TempDir Path tempDir) throws Exception {
        Path report = tempDir.resolve("scan-profile.json");
        JsonScanProfileSinkAdapter sink = new JsonScanProfileSinkAdapter(report);
        ConditionValidationReport validationReport = new ConditionValidationReport(List.of(
                new ConditionValidationIssue(
                        new SourceLocation(null, null, 7),
                        "\"Quoted\"",
                        "QuotedSymbol")
        ));

        sink.publish(ScanProfile.empty(), validationReport);

        String json = Files.readString(report);
        assertThat(json)
                .contains("\"className\":\"\"")
                .contains("\"methodName\":\"\"")
                .contains("\"template\":\"\"")
                .contains("\"expressionPreview\":\"\\\"Quoted\\\"\"");
    }

    @Test
    void wrapsIoFailures(@TempDir Path tempDir) throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("profile-as-directory.json"));
        JsonScanProfileSinkAdapter sink = new JsonScanProfileSinkAdapter(directory);
        ScanProfile profile = ScanProfile.empty();

        assertThatThrownBy(() -> sink.publish(profile))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to write scan profile report");
    }
}
