package de.burger.forensics.plugin.btmgen.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.createSampleSource;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.projectWithBuildDirectory;
import static de.burger.forensics.plugin.btmgen.maven.MavenMojoTestSupport.setField;

class MavenAnalysisStoreParityTest {

    @Test
    void btmGenMojoWritesAnalysisStoreManifestAndChecksums(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = createSampleSource(tempDir.resolve("src/main/java"));
        Path outputFile = tempDir.resolve("target/forensics/generated.btm");
        Path analysisStoreDirectory = tempDir.resolve("target/forensics/analysis-store");
        Path manifestFile = tempDir.resolve("target/forensics/manifest.json");
        Path checksumsFile = tempDir.resolve("target/forensics/checksums.sha256");
        BtmGenMojo mojo = new BtmGenMojo();
        mojo.setLog(new MavenMojoTestSupport.SilentLog());
        setField(mojo, "project", projectWithBuildDirectory(tempDir));
        setField(mojo, "sourceRoot", sourceRoot.toFile());
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("target/forensics/cache/scan-cache").toFile());
        setField(mojo, "analysisStoreEnabled", true);
        setField(mojo, "analysisStoreDirectory", analysisStoreDirectory.toFile());
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "checksumsFile", checksumsFile.toFile());
        setField(mojo, "profileReportFile", tempDir.resolve("target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 2);

        mojo.execute();

        assertThat(outputFile).exists();
        assertThat(analysisStoreDirectory.resolve("analysis-store.mv.db")).exists();
        assertThat(Files.readString(manifestFile)).contains("\"projectKey\": \"de.burger.forensics:sample\"");
        assertThat(Files.readString(checksumsFile)).contains("generated.btm", "manifest.json", "analysis-store/");
    }
}
