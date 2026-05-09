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
        List<String> emptySources = List.of();
        List<String> blankSources = List.of(" ");

        SemanticAnalysisRequest request = new SemanticAnalysisRequest(
                identity,
                List.of("src/main/java"),
                "build/workspace",
                "build/joern");

        assertThat(request.identity()).isEqualTo(identity);
        assertThat(request.sourceRoots()).containsExactly("src/main/java");
        assertThatThrownBy(() -> requestWithIdentity(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> requestWithSources(emptySources))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestWithSources(blankSources))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestWithWorkspace(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestWithWorkspace(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestWithOutputDirectory(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requestWithOutputDirectory(" "))
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
        assertThatThrownBy(() -> resultWithProviderVersion(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resultWithProviderVersion(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resultWithFingerprint(null))
                .isInstanceOf(IllegalArgumentException.class);
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

        assertThatThrownBy(() -> nodeWithId(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nodeWithType(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nodeWithFile(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nodeWithMethod(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nodeWithLine(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edgeWithId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edgeWithSource(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edgeWithTarget(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edgeWithType(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> methodWithId(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> methodWithFile(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> methodWithClassName(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> methodWithName(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> methodWithLine(0))
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

        assertThatThrownBy(() -> callWithCaller(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> callWithCallee(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> callWithNode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controlFlowWithSource(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controlFlowWithTarget(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controlFlowWithType(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dataFlowStepWithNode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dataFlowStepWithOrder(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dataFlowStepWithKind(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dataFlowPathWithId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dataFlowPathWithSource(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dataFlowPathWithTarget(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anchorWithEventKey(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anchorWithNode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anchorWithFile(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anchorWithMethod(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anchorWithLine(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anchorWithConfidence(-0.1d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anchorWithConfidence(1.1d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> anchorWithStrategy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SemanticAnalysisRequest requestWithIdentity(BuildIdentity identity) {
        return new SemanticAnalysisRequest(identity, List.of("src"), "work", "out");
    }

    private static SemanticAnalysisRequest requestWithSources(List<String> sourceRoots) {
        return new SemanticAnalysisRequest(identity(), sourceRoots, "work", "out");
    }

    private static SemanticAnalysisRequest requestWithWorkspace(String workspaceDirectory) {
        return new SemanticAnalysisRequest(identity(), List.of("src"), workspaceDirectory, "out");
    }

    private static SemanticAnalysisRequest requestWithOutputDirectory(String outputDirectory) {
        return new SemanticAnalysisRequest(identity(), List.of("src"), "work", outputDirectory);
    }

    private static SemanticAnalysisResult resultWithProviderVersion(String providerVersion) {
        return SemanticAnalysisResult.empty(providerVersion, "sha256:x");
    }

    private static SemanticAnalysisResult resultWithFingerprint(String semanticFingerprint) {
        return SemanticAnalysisResult.empty("joern", semanticFingerprint);
    }

    private static SemanticNode nodeWithId(String nodeId) {
        return new SemanticNode(nodeId, "CALL", "Demo.java", "Demo", "run", null, 1, null);
    }

    private static SemanticNode nodeWithType(String nodeType) {
        return new SemanticNode("n1", nodeType, "Demo.java", "Demo", "run", null, 1, null);
    }

    private static SemanticNode nodeWithFile(String filePath) {
        return new SemanticNode("n1", "CALL", filePath, "Demo", "run", null, 1, null);
    }

    private static SemanticNode nodeWithMethod(String methodName) {
        return new SemanticNode("n1", "CALL", "Demo.java", "Demo", methodName, null, 1, null);
    }

    private static SemanticNode nodeWithLine(int line) {
        return new SemanticNode("n1", "CALL", "Demo.java", "Demo", "run", null, line, null);
    }

    private static SemanticEdge edgeWithId(String edgeId) {
        return new SemanticEdge(edgeId, "n1", "n2", "CALL");
    }

    private static SemanticEdge edgeWithSource(String sourceNodeId) {
        return new SemanticEdge("e1", sourceNodeId, "n2", "CALL");
    }

    private static SemanticEdge edgeWithTarget(String targetNodeId) {
        return new SemanticEdge("e1", "n1", targetNodeId, "CALL");
    }

    private static SemanticEdge edgeWithType(String edgeType) {
        return new SemanticEdge("e1", "n1", "n2", edgeType);
    }

    private static SemanticMethod methodWithId(String methodId) {
        return new SemanticMethod(methodId, "Demo.java", "Demo", "run", null, 1);
    }

    private static SemanticMethod methodWithFile(String filePath) {
        return new SemanticMethod("m1", filePath, "Demo", "run", null, 1);
    }

    private static SemanticMethod methodWithClassName(String className) {
        return new SemanticMethod("m1", "Demo.java", className, "run", null, 1);
    }

    private static SemanticMethod methodWithName(String methodName) {
        return new SemanticMethod("m1", "Demo.java", "Demo", methodName, null, 1);
    }

    private static SemanticMethod methodWithLine(int line) {
        return new SemanticMethod("m1", "Demo.java", "Demo", "run", null, line);
    }

    private static CallRelation callWithCaller(String callerMethodId) {
        return new CallRelation(callerMethodId, "m2", "n1");
    }

    private static CallRelation callWithCallee(String calleeMethodId) {
        return new CallRelation("m1", calleeMethodId, "n1");
    }

    private static CallRelation callWithNode(String callNodeId) {
        return new CallRelation("m1", "m2", callNodeId);
    }

    private static ControlFlowRelation controlFlowWithSource(String sourceNodeId) {
        return new ControlFlowRelation(sourceNodeId, "n2", "NEXT");
    }

    private static ControlFlowRelation controlFlowWithTarget(String targetNodeId) {
        return new ControlFlowRelation("n1", targetNodeId, "NEXT");
    }

    private static ControlFlowRelation controlFlowWithType(String relationType) {
        return new ControlFlowRelation("n1", "n2", relationType);
    }

    private static DataFlowStep dataFlowStepWithNode(String nodeId) {
        return new DataFlowStep(nodeId, 0, "SOURCE");
    }

    private static DataFlowStep dataFlowStepWithOrder(int orderIndex) {
        return new DataFlowStep("n1", orderIndex, "SOURCE");
    }

    private static DataFlowStep dataFlowStepWithKind(String stepKind) {
        return new DataFlowStep("n1", 0, stepKind);
    }

    private static DataFlowPath dataFlowPathWithId(String pathId) {
        return new DataFlowPath(pathId, "n1", "n2", List.of());
    }

    private static DataFlowPath dataFlowPathWithSource(String sourceNodeId) {
        return new DataFlowPath("p1", sourceNodeId, "n2", List.of());
    }

    private static DataFlowPath dataFlowPathWithTarget(String targetNodeId) {
        return new DataFlowPath("p1", "n1", targetNodeId, List.of());
    }

    private static SemanticAnchor anchorWithEventKey(String scanEventKey) {
        return new SemanticAnchor(scanEventKey, "n1", "Demo.java", "Demo", "run", null, 1, null, 0.1d, "LINE_ONLY");
    }

    private static SemanticAnchor anchorWithNode(String semanticNodeId) {
        return new SemanticAnchor("event", semanticNodeId, "Demo.java", "Demo", "run", null, 1, null, 0.1d, "LINE_ONLY");
    }

    private static SemanticAnchor anchorWithFile(String filePath) {
        return new SemanticAnchor("event", "n1", filePath, "Demo", "run", null, 1, null, 0.1d, "LINE_ONLY");
    }

    private static SemanticAnchor anchorWithMethod(String methodName) {
        return new SemanticAnchor("event", "n1", "Demo.java", "Demo", methodName, null, 1, null, 0.1d, "LINE_ONLY");
    }

    private static SemanticAnchor anchorWithLine(int line) {
        return new SemanticAnchor("event", "n1", "Demo.java", "Demo", "run", null, line, null, 0.1d, "LINE_ONLY");
    }

    private static SemanticAnchor anchorWithConfidence(double confidence) {
        return new SemanticAnchor("event", "n1", "Demo.java", "Demo", "run", null, 1, null, confidence, "LINE_ONLY");
    }

    private static SemanticAnchor anchorWithStrategy(String matchStrategy) {
        return new SemanticAnchor("event", "n1", "Demo.java", "Demo", "run", null, 1, null, 0.1d, matchStrategy);
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
