package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for submitting a Gradle build context to the Forensics Analytics server.
 */
public class BtmGenExtension {
    private final Property<String> serverHost;
    private final Property<Integer> serverPort;
    private final Property<Boolean> plaintext;
    private final Property<Integer> deadlineSeconds;
    private final Property<String> schemaVersion;
    private final Property<String> projectId;
    private final Property<String> repositoryUrl;
    private final Property<String> branchName;
    private final Property<String> commitHash;
    private final Property<String> buildId;
    private final Property<String> scanTimestamp;
    private final Property<String> moduleName;
    private final Property<String> modulePath;

    @Inject
    public BtmGenExtension(ObjectFactory objects) {
        this.serverHost = objects.property(String.class);
        this.serverPort = objects.property(Integer.class);
        this.plaintext = objects.property(Boolean.class);
        this.deadlineSeconds = objects.property(Integer.class);
        this.schemaVersion = objects.property(String.class);
        this.projectId = objects.property(String.class);
        this.repositoryUrl = objects.property(String.class);
        this.branchName = objects.property(String.class);
        this.commitHash = objects.property(String.class);
        this.buildId = objects.property(String.class);
        this.scanTimestamp = objects.property(String.class);
        this.moduleName = objects.property(String.class);
        this.modulePath = objects.property(String.class);

        this.serverHost.convention("localhost");
        this.serverPort.convention(6565);
        this.plaintext.convention(true);
        this.deadlineSeconds.convention(30);
        this.schemaVersion.convention("1");
        this.repositoryUrl.convention("UNKNOWN");
        this.branchName.convention("UNKNOWN");
        this.commitHash.convention("UNKNOWN");
        this.scanTimestamp.convention("1970-01-01T00:00:00Z");
    }

    public Property<String> getServerHost() {
        return serverHost;
    }

    public Property<Integer> getServerPort() {
        return serverPort;
    }

    public Property<Boolean> getPlaintext() {
        return plaintext;
    }

    public Property<Integer> getDeadlineSeconds() {
        return deadlineSeconds;
    }

    public Property<String> getSchemaVersion() {
        return schemaVersion;
    }

    public Property<String> getProjectId() {
        return projectId;
    }

    public Property<String> getRepositoryUrl() {
        return repositoryUrl;
    }

    public Property<String> getBranchName() {
        return branchName;
    }

    public Property<String> getCommitHash() {
        return commitHash;
    }

    public Property<String> getBuildId() {
        return buildId;
    }

    public Property<String> getScanTimestamp() {
        return scanTimestamp;
    }

    public Property<String> getModuleName() {
        return moduleName;
    }

    public Property<String> getModulePath() {
        return modulePath;
    }
}
