package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.grpc.BuildContextPayloadFactory;
import de.burger.forensics.plugin.btmgen.grpc.ForensicIngestionClient;
import de.burger.forensics.plugin.btmgen.grpc.ForensicsSubmission;
import de.burger.forensics.plugin.btmgen.grpc.GrpcForensicIngestionClient;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.time.Duration;
import java.util.Objects;

/**
 * Sends a lightweight Gradle build context to the Forensics Analytics server.
 */
@DisableCachingByDefault(because = "The task performs a remote gRPC side effect.")
public abstract class SubmitForensicsAnalysisTask extends DefaultTask {
    private static final String PLUGIN_NAME = "forensics-tracing";

    @Input
    public abstract Property<String> getServerHost();

    @Input
    public abstract Property<Integer> getServerPort();

    @Input
    public abstract Property<Boolean> getPlaintext();

    @Input
    public abstract Property<Integer> getDeadlineSeconds();

    @Input
    public abstract Property<String> getSchemaVersion();

    @Input
    public abstract Property<String> getProjectId();

    @Input
    public abstract Property<String> getRepositoryUrl();

    @Input
    public abstract Property<String> getBranchName();

    @Input
    public abstract Property<String> getCommitHash();

    @Input
    public abstract Property<String> getBuildId();

    @Input
    public abstract Property<String> getScanTimestamp();

    @Input
    public abstract Property<String> getModuleName();

    @Input
    public abstract Property<String> getModulePath();

    @Input
    public abstract Property<String> getPluginVersion();

    public void configureFrom(Project project, BtmGenExtension extension) {
        getServerHost().convention(extension.getServerHost());
        getServerPort().convention(extension.getServerPort());
        getPlaintext().convention(extension.getPlaintext());
        getDeadlineSeconds().convention(extension.getDeadlineSeconds());
        getSchemaVersion().convention(extension.getSchemaVersion());
        getProjectId().convention(extension.getProjectId().orElse(project.getName()));
        getRepositoryUrl().convention(extension.getRepositoryUrl());
        getBranchName().convention(extension.getBranchName());
        getCommitHash().convention(extension.getCommitHash());
        getBuildId().convention(extension.getBuildId().orElse(defaultBuildId(project)));
        getScanTimestamp().convention(extension.getScanTimestamp());
        getModuleName().convention(extension.getModuleName().orElse(project.getName()));
        getModulePath().convention(extension.getModulePath().orElse(project.getPath()));
        getPluginVersion().convention(project.getVersion().toString());
    }

    @TaskAction
    public void submit() {
        try (ForensicIngestionClient client = createClient()) {
            var result = client.submit(toSubmission());
            getLogger().lifecycle(
                    "Submitted forensics analysis session {} with status {} ({} uploaded payload(s)).",
                    result.sessionId(),
                    result.status(),
                    result.uploadedPayloads());
        } catch (RuntimeException exception) {
            throw new GradleException("Failed to submit forensics analysis over gRPC: " + exception.getMessage(), exception);
        }
    }

    ForensicsSubmission toSubmission() {
        return new ForensicsSubmission(
                requireText(getSchemaVersion().get(), "schemaVersion"),
                requireText(getProjectId().get(), "projectId"),
                requireText(getRepositoryUrl().get(), "repositoryUrl"),
                requireText(getBranchName().get(), "branchName"),
                requireText(getCommitHash().get(), "commitHash"),
                requireText(getBuildId().get(), "buildId"),
                requireText(getScanTimestamp().get(), "scanTimestamp"),
                requireText(getModuleName().get(), "moduleName"),
                requireText(getModulePath().get(), "modulePath"),
                PLUGIN_NAME,
                requireText(getPluginVersion().get(), "pluginVersion"),
                java.util.List.of(BuildContextPayloadFactory.create(
                        getModuleName().get(),
                        getModulePath().get(),
                        getProjectId().get()))
        );
    }

    protected ForensicIngestionClient createClient() {
        return GrpcForensicIngestionClient.connect(
                requireText(getServerHost().get(), "serverHost"),
                getServerPort().get(),
                getPlaintext().getOrElse(true),
                Duration.ofSeconds(getDeadlineSeconds().get()));
    }

    private static String defaultBuildId(Project project) {
        return project.getName() + ":" + project.getVersion();
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNullElse(value, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
