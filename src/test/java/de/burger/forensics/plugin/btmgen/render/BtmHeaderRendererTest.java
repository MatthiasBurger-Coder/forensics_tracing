package de.burger.forensics.plugin.btmgen.render;

import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BtmHeaderRendererTest {

    @Test
    void rendersHeaderLinesWithSanitizedLineBreaks() {
        BuildIdentity identity = new BuildIdentity(
                "demo\nproject",
                new AnalysisRunId("run-1"),
                new BuildId("build-1"),
                new SourceFingerprint("sha256:source"),
                BuildIdentity.NOT_COMPUTED,
                "sha256:rules\r\nnext",
                BuildIdentity.NOT_COMPUTED,
                "test",
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);

        assertThat(new BtmHeaderRenderer().render(identity))
                .contains(
                        "# Forensics Analysis",
                        "# projectKey: demo project",
                        "# btmRulesFingerprint: sha256:rules  next");
    }
}
