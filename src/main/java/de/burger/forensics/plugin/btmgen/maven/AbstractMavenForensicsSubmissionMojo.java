package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.grpc.BuildContextPayloadFactory;
import de.burger.forensics.plugin.btmgen.grpc.ForensicIngestionClient;
import de.burger.forensics.plugin.btmgen.grpc.ForensicsSubmission;
import de.burger.forensics.plugin.btmgen.grpc.GrpcForensicIngestionClient;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

abstract class AbstractMavenForensicsSubmissionMojo extends AbstractMojo {
    private static final String PLUGIN_NAME = "forensics-tracing";
    private static final String UNKNOWN = "UNKNOWN";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forensics.serverHost", defaultValue = "localhost")
    private String serverHost = "localhost";

    @Parameter(property = "forensics.serverPort", defaultValue = "6565")
    private int serverPort = 6565;

    @Parameter(property = "forensics.plaintext", defaultValue = "true")
    private boolean plaintext = true;

    @Parameter(property = "forensics.deadlineSeconds", defaultValue = "30")
    private int deadlineSeconds = 30;

    @Parameter(property = "forensics.schemaVersion", defaultValue = "1")
    private String schemaVersion = "1";

    @Parameter(property = "forensics.projectId")
    private String projectId;

    @Parameter(property = "forensics.repositoryUrl", defaultValue = UNKNOWN)
    private String repositoryUrl = UNKNOWN;

    @Parameter(property = "forensics.branchName", defaultValue = UNKNOWN)
    private String branchName = UNKNOWN;

    @Parameter(property = "forensics.commitHash", defaultValue = UNKNOWN)
    private String commitHash = UNKNOWN;

    @Parameter(property = "forensics.buildId")
    private String buildId;

    @Parameter(property = "forensics.scanTimestamp", defaultValue = "1970-01-01T00:00:00Z")
    private String scanTimestamp = "1970-01-01T00:00:00Z";

    @Parameter(property = "forensics.moduleName")
    private String moduleName;

    @Parameter(property = "forensics.modulePath")
    private String modulePath;

    @Parameter(property = "forensics.pluginVersion")
    private String pluginVersion;

    @Override
    public final void execute() throws MojoExecutionException {
        try (ForensicIngestionClient client = createClient()) {
            var result = client.submit(toSubmission());
            getLog().info("Submitted forensics analysis session " + result.sessionId()
                    + " with status " + result.status()
                    + " from Maven goal " + goalName()
                    + " (" + result.uploadedPayloads() + " uploaded payload(s)).");
        } catch (RuntimeException exception) {
            throw new MojoExecutionException(
                    "Failed to submit forensics analysis over gRPC: " + exception.getMessage(),
                    exception);
        }
    }

    protected abstract String goalName();

    protected ForensicIngestionClient createClient() {
        return GrpcForensicIngestionClient.connect(
                requireText(serverHost, "serverHost"),
                serverPort,
                plaintext,
                Duration.ofSeconds(deadlineSeconds));
    }

    final ForensicsSubmission toSubmission() {
        String resolvedProjectId = blankToDefault(projectId, defaultProjectId());
        String resolvedModuleName = blankToDefault(moduleName, defaultModuleName());
        String resolvedModulePath = blankToDefault(modulePath, defaultModulePath());
        return new ForensicsSubmission(
                requireText(schemaVersion, "schemaVersion"),
                requireText(resolvedProjectId, "projectId"),
                requireText(repositoryUrl, "repositoryUrl"),
                requireText(branchName, "branchName"),
                requireText(commitHash, "commitHash"),
                requireText(blankToDefault(buildId, defaultBuildId()), "buildId"),
                requireText(scanTimestamp, "scanTimestamp"),
                requireText(resolvedModuleName, "moduleName"),
                requireText(resolvedModulePath, "modulePath"),
                PLUGIN_NAME,
                requireText(blankToDefault(pluginVersion, defaultPluginVersion()), "pluginVersion"),
                List.of(BuildContextPayloadFactory.create(
                        resolvedModuleName,
                        resolvedModulePath,
                        resolvedProjectId))
        );
    }

    private String defaultProjectId() {
        return blankToDefault(groupId(), UNKNOWN) + ":" + blankToDefault(artifactId(), UNKNOWN);
    }

    private String defaultBuildId() {
        return artifactId() + ":" + defaultPluginVersion();
    }

    private String defaultModuleName() {
        return blankToDefault(artifactId(), UNKNOWN);
    }

    private String defaultPluginVersion() {
        return blankToDefault(project == null ? null : project.getVersion(), UNKNOWN);
    }

    private String defaultModulePath() {
        if (project == null) {
            return UNKNOWN;
        }
        if (project.getBasedir() != null) {
            return project.getBasedir().toPath().toAbsolutePath().normalize().toString();
        }
        if (project.getFile() != null && project.getFile().getParentFile() != null) {
            return project.getFile().getParentFile().toPath().toAbsolutePath().normalize().toString();
        }
        return UNKNOWN;
    }

    private String groupId() {
        return project == null ? null : project.getGroupId();
    }

    private String artifactId() {
        return blankToDefault(project == null ? null : project.getArtifactId(), UNKNOWN);
    }

    private static String blankToDefault(String value, String defaultValue) {
        String normalized = Objects.requireNonNullElse(value, defaultValue);
        return normalized.isBlank() ? defaultValue : normalized;
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNullElse(value, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
