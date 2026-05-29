package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.grpc.ForensicIngestionClient;
import de.burger.forensics.plugin.btmgen.grpc.ForensicsSubmission;
import de.burger.forensics.plugin.btmgen.grpc.ForensicsSubmissionResult;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenForensicsSubmissionMojoTest {

    @Test
    void btmgenGoalDelegatesToGrpcClientBoundary(@TempDir Path tempDir) throws Exception {
        RecordingBtmGenMojo.submission = null;
        RecordingBtmGenMojo mojo = new RecordingBtmGenMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", project(tempDir));
        setField(mojo, "projectId", "project-a");
        setField(mojo, "repositoryUrl", "https://example.test/repo.git");
        setField(mojo, "commitHash", "abc123");
        setField(mojo, "buildId", "build-42");

        mojo.execute();

        assertThat(RecordingBtmGenMojo.submission).isNotNull();
        assertThat(RecordingBtmGenMojo.submission.projectId()).isEqualTo("project-a");
        assertThat(RecordingBtmGenMojo.submission.repositoryUrl()).isEqualTo("https://example.test/repo.git");
        assertThat(RecordingBtmGenMojo.submission.commitHash()).isEqualTo("abc123");
        assertThat(RecordingBtmGenMojo.submission.buildId()).isEqualTo("build-42");
        assertThat(RecordingBtmGenMojo.submission.moduleName()).isEqualTo("sample");
        assertThat(RecordingBtmGenMojo.submission.modulePath()).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
    }

    @Test
    void createsDiagnosticPayloadWithoutLocalAnalysisArtifacts(@TempDir Path tempDir) throws Exception {
        RecordingSubmitMojo mojo = new RecordingSubmitMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", project(tempDir));
        setField(mojo, "projectId", "project-a");
        setField(mojo, "repositoryUrl", "https://example.test/repo.git");
        setField(mojo, "commitHash", "abc123");

        ForensicsSubmission submission = mojo.toSubmission();

        assertThat(submission.schemaVersion()).isEqualTo("1");
        assertThat(submission.pluginName()).isEqualTo("forensics-tracing");
        assertThat(submission.pluginVersion()).isEqualTo("1.0.0");
        assertThat(submission.payloads()).hasSize(1);
        var payload = submission.payloads().get(0);
        assertThat(payload.payloadId()).isEqualTo("build-context");
        assertThat(payload.attributes()).containsEntry("artifact", "build-context");
        assertThat(new String(payload.content(), StandardCharsets.UTF_8)).contains(
                "\"moduleName\": \"sample\"",
                "\"projectId\": \"project-a\"");
    }

    @Test
    void wrapsGrpcFailuresAsMojoExecutionException(@TempDir Path tempDir) throws Exception {
        FailingBtmGenMojo mojo = new FailingBtmGenMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", project(tempDir));

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Failed to submit forensics analysis over gRPC");
    }

    @Test
    void rejectsBlankServerHostBeforeOpeningGrpcChannel(@TempDir Path tempDir) throws Exception {
        SubmitAnalysisMojo mojo = new SubmitAnalysisMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", project(tempDir));
        setField(mojo, "serverHost", " ");

        assertThatThrownBy(mojo::createClient)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serverHost must not be blank");
    }

    @Test
    void keepsLegacyGoalNamesAsGrpcSubmissionAliases() {
        assertThat(new SubmitAnalysisMojo().goalName()).isEqualTo("submit-analysis");
        assertThat(new BtmGenMojo().goalName()).isEqualTo("btmgen");
        assertThat(new AnalyzeMojo().goalName()).isEqualTo("analyze");
        assertThat(new BtmGenAggregateMojo().goalName()).isEqualTo("btmgen-aggregate");
        assertThat(new AnalyzeAggregateMojo().goalName()).isEqualTo("analyze-aggregate");
        assertThat(new AnalyzeSemanticsMojo().goalName()).isEqualTo("analyze-semantics");
        assertThat(new ImportSemanticsMojo().goalName()).isEqualTo("import-semantics");
    }

    @Test
    void cleanAnalysisGoalDoesNotOwnLocalArtifacts() {
        CleanForensicsAnalysisMojo mojo = new CleanForensicsAnalysisMojo();
        mojo.setLog(new SilentLog());

        mojo.execute();
    }

    @Test
    void defaultsBuildContextWhenMavenProjectIsMissing() {
        RecordingSubmitMojo mojo = new RecordingSubmitMojo();
        mojo.setLog(new SilentLog());

        ForensicsSubmission submission = mojo.toSubmission();

        assertThat(submission.projectId()).isEqualTo("UNKNOWN:UNKNOWN");
        assertThat(submission.buildId()).isEqualTo("UNKNOWN:UNKNOWN");
        assertThat(submission.moduleName()).isEqualTo("UNKNOWN");
        assertThat(submission.modulePath()).isEqualTo("UNKNOWN");
        assertThat(submission.pluginVersion()).isEqualTo("UNKNOWN");
    }

    @Test
    void blankMavenOverridesUseProjectDefaults(@TempDir Path tempDir) throws Exception {
        RecordingSubmitMojo mojo = new RecordingSubmitMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", project(tempDir));
        setField(mojo, "projectId", " ");
        setField(mojo, "buildId", " ");
        setField(mojo, "moduleName", " ");
        setField(mojo, "modulePath", " ");
        setField(mojo, "pluginVersion", " ");

        ForensicsSubmission submission = mojo.toSubmission();

        assertThat(submission.projectId()).isEqualTo("de.burger.forensics:sample");
        assertThat(submission.buildId()).isEqualTo("sample:1.0.0");
        assertThat(submission.moduleName()).isEqualTo("sample");
        assertThat(submission.modulePath()).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
        assertThat(submission.pluginVersion()).isEqualTo("1.0.0");
    }

    @Test
    void usesPomParentWhenBasedirIsUnavailable(@TempDir Path tempDir) throws Exception {
        RecordingSubmitMojo mojo = new RecordingSubmitMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", projectWithoutBasedir(tempDir.resolve("pom.xml").toFile()));

        ForensicsSubmission submission = mojo.toSubmission();

        assertThat(submission.modulePath()).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
    }

    @Test
    void usesUnknownModulePathWhenMavenProjectHasNoDirectories() throws Exception {
        RecordingSubmitMojo mojo = new RecordingSubmitMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", projectWithoutDirectories());

        ForensicsSubmission submission = mojo.toSubmission();

        assertThat(submission.modulePath()).isEqualTo("UNKNOWN");
    }

    @Test
    void usesUnknownModulePathWhenPomFileHasNoParent() throws Exception {
        RecordingSubmitMojo mojo = new RecordingSubmitMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", projectWithoutBasedir(new File("pom.xml")));

        ForensicsSubmission submission = mojo.toSubmission();

        assertThat(submission.modulePath()).isEqualTo("UNKNOWN");
    }

    @Test
    void createsGrpcClientFromDefaultMavenParameters() {
        SubmitAnalysisMojo mojo = new SubmitAnalysisMojo();
        mojo.setLog(new SilentLog());

        try (ForensicIngestionClient client = mojo.createClient()) {
            assertThat(client).isNotNull();
        }
    }

    private static MavenProject project(Path basedir) {
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId("sample");
        model.setVersion("1.0.0");
        MavenProject project = new MavenProject(model);
        project.setFile(basedir.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(basedir.resolve("target").toString());
        project.setBuild(build);
        return project;
    }

    private static MavenProject projectWithoutBasedir(File pomFile) {
        return new MavenProject(sampleModel()) {
            @Override
            public File getBasedir() {
                return null;
            }

            @Override
            public File getFile() {
                return pomFile;
            }
        };
    }

    private static MavenProject projectWithoutDirectories() {
        return new MavenProject(sampleModel()) {
            @Override
            public File getBasedir() {
                return null;
            }

            @Override
            public File getFile() {
                return null;
            }
        };
    }

    private static Model sampleModel() {
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId("sample");
        model.setVersion("1.0.0");
        return model;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static class RecordingBtmGenMojo extends BtmGenMojo {
        private static ForensicsSubmission submission;

        @Override
        protected ForensicIngestionClient createClient() {
            return new RecordingClient(request -> submission = request);
        }
    }

    public static class RecordingSubmitMojo extends SubmitAnalysisMojo {
        @Override
        protected ForensicIngestionClient createClient() {
            return new RecordingClient(ignored -> {
                // This test calls toSubmission directly.
            });
        }
    }

    public static class FailingBtmGenMojo extends BtmGenMojo {
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

    private record RecordingClient(SubmissionRecorder recorder) implements ForensicIngestionClient {
        @Override
        public ForensicsSubmissionResult submit(ForensicsSubmission request) {
            recorder.record(request);
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
    }

    private interface SubmissionRecorder {
        void record(ForensicsSubmission submission);
    }

    private static final class SilentLog implements Log {
        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void debug(Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void info(Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void warn(Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }

        @Override
        public void error(Throwable error) {
            // Intentionally silent; the test only needs a Maven Log implementation.
        }
    }
}
