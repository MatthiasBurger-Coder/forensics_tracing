package de.burger.forensics.domain.model.analysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildIdentityTest {

    @Test
    void rejectsBlankProjectKey() {
        assertThatThrownBy(() -> identity(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project key");
    }

    @Test
    void defaultsOptionalFingerprintsAndPluginVersion() {
        BuildIdentity identity = new BuildIdentity(
                "demo",
                new AnalysisRunId("run-1"),
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                " ",
                null,
                "",
                null,
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);

        assertThat(identity.classpathFingerprint()).isEqualTo(BuildIdentity.NOT_COMPUTED);
        assertThat(identity.btmRulesFingerprint()).isEqualTo(BuildIdentity.NOT_COMPUTED);
        assertThat(identity.artifactFingerprint()).isEqualTo(BuildIdentity.NOT_COMPUTED);
        assertThat(identity.pluginVersion()).isEqualTo(BuildIdentity.UNKNOWN);
    }

    @Test
    void preservesConfiguredFingerprintsAndPluginVersion() {
        BuildIdentity identity = new BuildIdentity(
                "demo",
                new AnalysisRunId("run-1"),
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                "sha256:classpath",
                "sha256:rules",
                "sha256:artifact",
                "1.2.3",
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);

        assertThat(identity.classpathFingerprint()).isEqualTo("sha256:classpath");
        assertThat(identity.btmRulesFingerprint()).isEqualTo("sha256:rules");
        assertThat(identity.artifactFingerprint()).isEqualTo("sha256:artifact");
        assertThat(identity.pluginVersion()).isEqualTo("1.2.3");
    }

    private static BuildIdentity identity(String projectKey) {
        return new BuildIdentity(
                projectKey,
                new AnalysisRunId("run-1"),
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                BuildIdentity.NOT_COMPUTED,
                BuildIdentity.NOT_COMPUTED,
                BuildIdentity.NOT_COMPUTED,
                "test",
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);
    }
}
