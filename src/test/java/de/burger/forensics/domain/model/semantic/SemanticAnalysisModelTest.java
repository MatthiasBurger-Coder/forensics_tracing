package de.burger.forensics.domain.model.semantic;

import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAnalysisModelTest {

    @Test
    void requestRequiresIdentitySourcesAndDirectories() {
        BuildIdentity identity = identity();

        SemanticAnalysisRequest request = new SemanticAnalysisRequest(
                identity,
                List.of("src/main/java"),
                "build/workspace",
                "build/joern");

        assertThat(request.identity()).isEqualTo(identity);
        assertThat(request.sourceRoots()).containsExactly("src/main/java");
        assertThatThrownBy(() -> new SemanticAnalysisRequest(null, List.of("src"), "work", "out"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SemanticAnalysisRequest(identity, List.of(), "work", "out"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnalysisRequest(identity, List.of(" "), "work", "out"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnalysisRequest(identity, List.of("src"), null, "out"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnalysisRequest(identity, List.of("src"), " ", "out"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnalysisRequest(identity, List.of("src"), "work", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnalysisRequest(identity, List.of("src"), "work", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultCopiesCollectionsAndRejectsMissingIdentity() {
        ArtifactChecksum artifact = new ArtifactChecksum("joern/cpg.bin", "joern-cpg", "abc", 3L);
        SemanticAnalysisResult result = new SemanticAnalysisResult(
                "joern 1.0",
                "sha256:semantic",
                List.of(artifact),
                List.of(node()),
                List.of(edge()),
                List.of(method()),
                List.of(call()),
                List.of(controlFlow()),
                List.of(dataFlowPath()),
                List.of(anchor()));

        assertThat(result.artifacts()).containsExactly(artifact);
        assertThat(result.nodes()).containsExactly(node());
        assertThatThrownBy(() -> new SemanticAnalysisResult(
                null,
                "sha256:x",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnalysisResult(
                " ",
                "sha256:x",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnalysisResult(
                "joern",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(SemanticAnalysisResult.empty("UNKNOWN", "sha256:empty").nodes()).isEmpty();
    }

    @Test
    void graphModelsRejectBlankRequiredFieldsAndInvalidNumbers() {
        assertThat(node().normalizedCode()).isEqualTo("code");
        assertThat(edge().edgeType()).isEqualTo("CALL");
        assertThat(method().methodName()).isEqualTo("run");
        assertThat(call().callNodeId()).isEqualTo("n1");
        assertThat(controlFlow().relationType()).isEqualTo("NEXT");
        assertThat(dataFlowStep().orderIndex()).isZero();
        assertThat(dataFlowPath().steps()).containsExactly(dataFlowStep());

        assertThatThrownBy(() -> new SemanticNode(" ", "CALL", "Demo.java", "Demo", "run", null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticNode("n1", null, "Demo.java", "Demo", "run", null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticNode("n1", "CALL", null, "Demo", "run", null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticNode("n1", "CALL", "Demo.java", "Demo", " ", null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticNode("n1", "CALL", "Demo.java", "Demo", "run", null, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticEdge(null, "n1", "n2", "CALL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticEdge("e1", " ", "n2", "CALL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticEdge("e1", "n1", null, "CALL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticEdge("e1", "n1", "n2", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticMethod(" ", "Demo.java", "Demo", "run", null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticMethod("m1", null, "Demo", "run", null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticMethod("m1", "Demo.java", " ", "run", null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticMethod("m1", "Demo.java", "Demo", null, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticMethod("m1", "Demo.java", "Demo", "run", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void relationAndAnchorModelsValidateRequiredFields() {
        assertThat(anchor().confidence()).isEqualTo(0.95d);
        assertThat(new SemanticAnchor(
                "event",
                "n1",
                "Demo.java",
                "Demo",
                "run",
                null,
                1,
                null,
                0.40d,
                "LINE_ONLY").normalizedCode()).isEmpty();

        assertThatThrownBy(() -> new CallRelation(null, "m2", "n1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CallRelation("m1", " ", "n1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CallRelation("m1", "m2", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ControlFlowRelation(null, "n2", "NEXT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ControlFlowRelation("n1", " ", "NEXT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ControlFlowRelation("n1", "n2", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataFlowStep(null, 0, "SOURCE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataFlowStep("n1", -1, "SOURCE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataFlowStep("n1", 0, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataFlowPath(null, "n1", "n2", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataFlowPath("p1", " ", "n2", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataFlowPath("p1", "n1", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnchor(null, "n1", "Demo.java", "Demo", "run", null, 1, null, 0.1d, "LINE_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnchor("event", null, "Demo.java", "Demo", "run", null, 1, null, 0.1d, "LINE_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnchor("event", "n1", null, "Demo", "run", null, 1, null, 0.1d, "LINE_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnchor("event", "n1", "Demo.java", "Demo", null, null, 1, null, 0.1d, "LINE_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnchor("event", "n1", "Demo.java", "Demo", "run", null, 0, null, 0.1d, "LINE_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnchor("event", "n1", "Demo.java", "Demo", "run", null, 1, null, -0.1d, "LINE_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnchor("event", "n1", "Demo.java", "Demo", "run", null, 1, null, 1.1d, "LINE_ONLY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAnchor("event", "n1", "Demo.java", "Demo", "run", null, 1, null, 0.1d, null))
                .isInstanceOf(IllegalArgumentException.class);
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

    private static SemanticNode node() {
        return new SemanticNode("n1", "CALL", "Demo.java", "demo.Demo", "run", "void run()", 12, "code");
    }

    private static SemanticEdge edge() {
        return new SemanticEdge("e1", "n1", "n2", "CALL");
    }

    private static SemanticMethod method() {
        return new SemanticMethod("m1", "Demo.java", "demo.Demo", "run", "void run()", 12);
    }

    private static CallRelation call() {
        return new CallRelation("m1", "m2", "n1");
    }

    private static ControlFlowRelation controlFlow() {
        return new ControlFlowRelation("n1", "n2", "NEXT");
    }

    private static DataFlowStep dataFlowStep() {
        return new DataFlowStep("n1", 0, "SOURCE");
    }

    private static DataFlowPath dataFlowPath() {
        return new DataFlowPath("p1", "n1", "n2", List.of(dataFlowStep()));
    }

    private static SemanticAnchor anchor() {
        return new SemanticAnchor(
                "demo.Demo#run:12:METHOD_ENTER",
                "n1",
                "Demo.java",
                "demo.Demo",
                "run",
                "void run()",
                12,
                "code",
                0.95d,
                "FQCN_METHOD_LINE_CODE");
    }
}
