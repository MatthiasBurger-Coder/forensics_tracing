package de.burger.forensics.plugin.btmgen.render;

import de.burger.forensics.domain.model.analysis.BuildIdentity;

import java.util.List;
import java.util.Objects;

/**
 * Renders Byteman-safe comment lines that bind a file to an analysis run.
 */
public final class BtmHeaderRenderer {

    public List<String> render(BuildIdentity identity) {
        Objects.requireNonNull(identity, "Build identity must not be null.");
        return List.of(
                "# Forensics Analysis",
                "# schemaVersion: " + safe(identity.schemaVersion().value()),
                "# projectKey: " + safe(identity.projectKey()),
                "# analysisRunId: " + safe(identity.analysisRunId().value()),
                "# buildId: " + safe(identity.buildId().value()),
                "# sourceFingerprint: " + safe(identity.sourceFingerprint().value()),
                "# btmRulesFingerprint: " + safe(identity.btmRulesFingerprint()),
                "# pluginVersion: " + safe(identity.pluginVersion()));
    }

    private static String safe(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }
}
