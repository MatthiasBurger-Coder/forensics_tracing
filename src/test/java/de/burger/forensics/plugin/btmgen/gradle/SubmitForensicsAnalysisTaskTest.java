package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.grpc.ForensicIngestionClient;
import de.burger.forensics.plugin.btmgen.grpc.ForensicsSubmission;
import de.burger.forensics.plugin.btmgen.grpc.ForensicsSubmissionResult;
import org.gradle.api.Project;
import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitForensicsAnalysisTaskTest {

    @Test
    void submitDelegatesToConfiguredGrpcClientBoundary() {
        RecordingSubmitTask.submission = null;
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.setVersion("1.2.3");
        BtmGenExtension extension = project.getExtensions().create("forensicsTracing", BtmGenExtension.class);
        extension.getProjectId().set("project-a");
        extension.getRepositoryUrl().set("https://example.test/repo.git");
        extension.getCommitHash().set("abc123");
        extension.getBuildId().set("build-42");
        RecordingSubmitTask task = project.getTasks()
                .register("recordSubmit", RecordingSubmitTask.class)
                .get();
        task.configureFrom(project, extension);

        task.submit();

        assertThat(RecordingSubmitTask.submission).isNotNull();
        assertThat(RecordingSubmitTask.submission.projectId()).isEqualTo("project-a");
    }

    @Test
    void createsDiagnosticPayloadWithoutLocalAnalysisArtifacts() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.getPlugins().apply("de.burger.forensics.btmgen");
        BtmGenExtension extension = project.getExtensions().getByType(BtmGenExtension.class);
        extension.getProjectId().set("project-a");
        extension.getRepositoryUrl().set("https://example.test/repo.git");
        extension.getCommitHash().set("abc123");
        extension.getBuildId().set("build-42");

        SubmitForensicsAnalysisTask task = (SubmitForensicsAnalysisTask) project.getTasks()
                .getByName("submitForensicsAnalysis");
        var submission = task.toSubmission();

        assertThat(submission.schemaVersion()).isEqualTo("1");
        assertThat(submission.pluginName()).isEqualTo("forensics-tracing");
        assertThat(submission.payloads()).hasSize(1);
        var payload = submission.payloads().get(0);
        assertThat(payload.payloadId()).isEqualTo("build-context");
        assertThat(payload.kind()).isEqualTo(de.burger.forensics.plugin.btmgen.grpc.ForensicsPayload.Kind.DIAGNOSTIC_REPORT);
        assertThat(payload.contentType()).isEqualTo("application/json");
        assertThat(new String(payload.content(), StandardCharsets.UTF_8)).contains(
                "\"moduleName\": \"demo\"",
                "\"modulePath\": \":\"",
                "\"projectId\": \"project-a\"");
    }

    @Test
    void rejectsBlankRequiredServerContractValuesBeforeCallingGrpc() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.getPlugins().apply("de.burger.forensics.btmgen");
        BtmGenExtension extension = project.getExtensions().getByType(BtmGenExtension.class);
        extension.getRepositoryUrl().set("");

        SubmitForensicsAnalysisTask task = (SubmitForensicsAnalysisTask) project.getTasks()
                .getByName("submitForensicsAnalysis");

        assertThatThrownBy(task::toSubmission)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repositoryUrl must not be blank");
    }

    @Test
    void escapesDiagnosticJsonValues() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.getPlugins().apply("de.burger.forensics.btmgen");
        BtmGenExtension extension = project.getExtensions().getByType(BtmGenExtension.class);
        extension.getProjectId().set("project\"\\\\\n\r\t");
        extension.getRepositoryUrl().set("https://example.test/repo.git");
        extension.getCommitHash().set("abc123");
        extension.getBuildId().set("build-42");
        extension.getModuleName().set("module\"\\\\\n\r\t");
        extension.getModulePath().set(":module\"\\\\\n\r\t");

        SubmitForensicsAnalysisTask task = (SubmitForensicsAnalysisTask) project.getTasks()
                .getByName("submitForensicsAnalysis");

        String json = new String(task.toSubmission().payloads().get(0).content(), StandardCharsets.UTF_8);

        assertThat(json).contains(
                "module\\\"\\\\\\\\\\n\\r\\t",
                ":module\\\"\\\\\\\\\\n\\r\\t",
                "project\\\"\\\\\\\\\\n\\r\\t");
    }

    @Test
    void rejectsBlankServerHostBeforeOpeningGrpcChannel() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.getPlugins().apply("de.burger.forensics.btmgen");
        SubmitForensicsAnalysisTask task = (SubmitForensicsAnalysisTask) project.getTasks()
                .getByName("submitForensicsAnalysis");
        task.getServerHost().set(" ");

        assertThatThrownBy(task::createClient)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serverHost must not be blank");
    }

    @Test
    void createsGrpcClientFromConfiguredTransportProperties() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.getPlugins().apply("de.burger.forensics.btmgen");
        SubmitForensicsAnalysisTask task = (SubmitForensicsAnalysisTask) project.getTasks()
                .getByName("submitForensicsAnalysis");

        try (ForensicIngestionClient client = task.createClient()) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void wrapsGrpcClientFailuresAsGradleException() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        BtmGenExtension extension = project.getExtensions().create("forensicsTracing", BtmGenExtension.class);
        extension.getRepositoryUrl().set("https://example.test/repo.git");
        extension.getCommitHash().set("abc123");
        FailingSubmitTask task = project.getTasks()
                .register("failingSubmit", FailingSubmitTask.class)
                .get();
        task.configureFrom(project, extension);

        assertThatThrownBy(task::submit)
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("Failed to submit forensics analysis over gRPC");
    }

    public abstract static class RecordingSubmitTask extends SubmitForensicsAnalysisTask {
        private static ForensicsSubmission submission;

        @Override
        protected ForensicIngestionClient createClient() {
            return new ForensicIngestionClient() {
                @Override
                public ForensicsSubmissionResult submit(ForensicsSubmission request) {
                    submission = request;
                    return new ForensicsSubmissionResult(
                            "session-1",
                            "INGESTION_STATUS_COMPLETED",
                            "completed",
                            request.payloads().size());
                }

                @Override
                public void close() {
                    // Nothing to close in the test double.
                }
            };
        }
    }

    public abstract static class FailingSubmitTask extends SubmitForensicsAnalysisTask {
        @Override
        protected ForensicIngestionClient createClient() {
            return new ForensicIngestionClient() {
                @Override
                public ForensicsSubmissionResult submit(ForensicsSubmission request) {
                    throw new IllegalStateException("server unavailable");
                }

                @Override
                public void close() {
                    // Nothing to close in the test double.
                }
            };
        }
    }
}
