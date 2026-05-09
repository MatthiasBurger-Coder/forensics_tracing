package de.burger.forensics.application.service;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisRequest;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;
import de.burger.forensics.domain.model.semantic.SemanticNode;
import de.burger.forensics.domain.port.out.SemanticAnalysisPort;
import de.burger.forensics.domain.port.out.SemanticAnalysisStorePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyzeSemanticsUseCaseTest {

    @Test
    void storesSemanticResultAndMarksImportCompleted() {
        SemanticAnalysisResult result = result(List.of(anchor("n1", 0.95d, "FQCN_METHOD_LINE_CODE")));
        RecordingStore store = new RecordingStore();
        AnalyzeSemanticsUseCase useCase = new AnalyzeSemanticsUseCase(request -> result, store);

        SemanticAnalysisResult actual = useCase.analyze(request());

        assertThat(actual).isEqualTo(result);
        assertThat(store.calls).containsExactly("create", "graph", "anchors:1", "status:COMPLETED");
    }

    @Test
    void marksImportFailedWhenStoreFailsAfterImportRunWasCreated() {
        SemanticAnalysisResult result = result(List.of());
        RecordingStore store = new RecordingStore();
        store.failGraph = true;
        AnalyzeSemanticsUseCase useCase = new AnalyzeSemanticsUseCase(request -> result, store);
        SemanticAnalysisRequest request = request();

        assertThatThrownBy(() -> useCase.analyze(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("store failed");
        assertThat(store.calls).containsExactly("create", "graph", "status:FAILED");
    }

    @Test
    void matcherPrefersStrongestSemanticAnchor() {
        ScanEvent event = new ScanEvent(
                new SourceLocation("demo.Demo", "run", 12),
                "void run()",
                RuleTemplate.METHOD_ENTER,
                "code",
                "java",
                "void");
        List<SemanticNode> nodes = List.of(
                new SemanticNode("line", "CALL", "Demo.java", "demo.Other", "other", null, 12, "different"),
                new SemanticNode("method", "CALL", "Demo.java", "demo.Other", "run", null, 12, "different"),
                new SemanticNode("strong", "CALL", "Demo.java", "demo.Demo", "run", "void run()", 12, "code"));

        List<SemanticAnchor> anchors = new SemanticAnchorMatcher().match(List.of(event), nodes);

        assertThat(anchors).singleElement()
                .satisfies(anchor -> {
                    assertThat(anchor.semanticNodeId()).isEqualTo("strong");
                    assertThat(anchor.confidence()).isEqualTo(0.95d);
                    assertThat(anchor.matchStrategy()).isEqualTo("FQCN_METHOD_LINE_CODE");
                });
    }

    @Test
    void matcherIgnoresEventsWithoutLocationAndReturnsLineFallback() {
        ScanEvent missingLocation = new ScanEvent(null, null, RuleTemplate.METHOD_ENTER, null, "java", null);
        ScanEvent lineOnly = new ScanEvent(
                new SourceLocation("demo.Demo", "missing", 40),
                null,
                RuleTemplate.METHOD_ENTER,
                null,
                "java",
                null);
        SemanticNode node = new SemanticNode("line", "CALL", "Demo.java", "demo.Other", "other", null, 40, "");

        List<SemanticAnchor> anchors = new SemanticAnchorMatcher().match(List.of(missingLocation, lineOnly), List.of(node));

        assertThat(anchors).singleElement()
                .satisfies(anchor -> {
                    assertThat(anchor.semanticNodeId()).isEqualTo("line");
                    assertThat(anchor.confidence()).isEqualTo(0.40d);
                    assertThat(anchor.matchStrategy()).isEqualTo("LINE_ONLY");
                });
    }

    @Test
    void useCaseRejectsNullDependenciesAndRequest() {
        SemanticAnalysisPort analysisPort = request -> result(List.of());
        RecordingStore store = new RecordingStore();
        AnalyzeSemanticsUseCase useCase = new AnalyzeSemanticsUseCase(analysisPort, store);

        assertThatThrownBy(() -> new AnalyzeSemanticsUseCase(null, store))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AnalyzeSemanticsUseCase(analysisPort, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> useCase.analyze(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static SemanticAnalysisRequest request() {
        return new SemanticAnalysisRequest(identity(), List.of("src/main/java"), "workspace", "joern");
    }

    private static SemanticAnalysisResult result(List<SemanticAnchor> anchors) {
        return new SemanticAnalysisResult(
                "joern 1.0",
                "sha256:semantic",
                List.of(),
                List.of(new SemanticNode("n1", "CALL", "Demo.java", "demo.Demo", "run", "void run()", 12, "code")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                anchors);
    }

    private static SemanticAnchor anchor(String nodeId, double confidence, String strategy) {
        return new SemanticAnchor(
                "demo.Demo#run:12:METHOD_ENTER",
                nodeId,
                "Demo.java",
                "demo.Demo",
                "run",
                "void run()",
                12,
                "code",
                confidence,
                strategy);
    }

    private static BuildIdentity identity() {
        return new BuildIdentity(
                "demo",
                new AnalysisRunId("run-1"),
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                BuildIdentity.NOT_COMPUTED,
                "sha256:rules",
                BuildIdentity.NOT_COMPUTED,
                "test",
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);
    }

    private static final class RecordingStore implements SemanticAnalysisStorePort {
        private final List<String> calls = new ArrayList<>();
        private boolean failGraph;

        @Override
        public void createSemanticImportRun(AnalysisRunId analysisRunId, SemanticAnalysisResult result) {
            calls.add("create");
        }

        @Override
        public void storeSemanticGraph(AnalysisRunId analysisRunId, SemanticAnalysisResult result) {
            calls.add("graph");
            if (failGraph) {
                throw new IllegalStateException("store failed");
            }
        }

        @Override
        public void storeSemanticAnchors(AnalysisRunId analysisRunId, List<SemanticAnchor> anchors) {
            calls.add("anchors:" + anchors.size());
        }

        @Override
        public void updateSemanticImportStatus(AnalysisRunId analysisRunId, String semanticFingerprint, String status) {
            calls.add("status:" + status);
        }
    }
}
