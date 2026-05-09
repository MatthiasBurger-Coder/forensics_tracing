package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.adapters.filesystem.ArtifactChecksumService;
import de.burger.forensics.adapters.persistence.h2.H2AnalysisStoreAdapter;
import de.burger.forensics.adaptersupport.joern.JoernAnalysisConfig;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.analysis.SourceFingerprint;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;
import de.burger.forensics.domain.model.semantic.SemanticNode;
import de.burger.forensics.domain.port.out.SemanticAnalysisPort;
import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyzeForensicsSemanticsTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void analyzeTaskFailsClearlyWhenJoernIsDisabled() {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var task = project.getTasks().register("analyzeDisabled", AnalyzeForensicsSemanticsTask.class).get();

        assertThatThrownBy(task::analyze)
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("joernEnabled=true");
    }

    @Test
    void importTaskFailsClearlyWhenJoernIsDisabled() {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var task = project.getTasks().register("importDisabled", ImportForensicsSemanticsTask.class).get();

        assertThatThrownBy(task::verifyImportedArtifacts)
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("joernEnabled=true");
    }

    @Test
    void importTaskRequiresGeneratedJoernArtifacts() {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var task = project.getTasks().register("importMissing", ImportForensicsSemanticsTask.class).get();
        task.getJoernEnabled().set(true);

        assertThatThrownBy(task::verifyImportedArtifacts)
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("Run analyzeForensicsSemantics first");
    }

    @Test
    void importTaskAcceptsExistingCallgraphArtifact() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var outputDirectory = Files.createDirectories(tempDir.resolve("joern"));
        Files.writeString(outputDirectory.resolve("callgraph.json"), "{}");
        var task = project.getTasks().register("importExisting", ImportForensicsSemanticsTask.class).get();
        task.getJoernEnabled().set(true);
        task.getJoernOutputDirectory().set(outputDirectory.toFile());

        task.verifyImportedArtifacts();

        assertThat(task.getJoernOutputDirectory().get().getAsFile()).isEqualTo(outputDirectory.toFile());
    }

    @Test
    void analyzeTaskImportsSemanticResultAndUpdatesManifestAndChecksums() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path forensicsDir = Files.createDirectories(tempDir.resolve("build/forensics"));
        Path manifest = forensicsDir.resolve("manifest.json");
        Path checksums = forensicsDir.resolve("checksums.sha256");
        Path btmFile = Files.writeString(forensicsDir.resolve("forensics.btm"), "RULE demo\nENDRULE\n");
        BuildIdentity identity = identity();
        writeManifest(manifest, identity);
        Path analysisStoreDirectory = Files.createDirectories(forensicsDir.resolve("analysis-store"));
        Path database = analysisStoreDirectory.resolve("analysis-store");
        H2AnalysisStoreAdapter store = new H2AnalysisStoreAdapter(database);
        store.initializeSchema();
        store.createAnalysisRun(identity);

        var task = project.getTasks().register("analyzeSuccess", TestAnalyzeForensicsSemanticsTask.class).get();
        task.getJoernEnabled().set(true);
        task.getJoernFailOnError().set(true);
        task.getJoernTimeoutSeconds().set(30);
        task.getSourceRoots().setFrom(sourceRoot.toFile());
        task.getJoernOutputDirectory().set(forensicsDir.resolve("joern").toFile());
        task.getJoernWorkspaceDirectory().set(forensicsDir.resolve("joern/workspace").toFile());
        task.getAnalysisStoreDirectory().set(analysisStoreDirectory.toFile());
        task.getManifestFile().set(manifest.toFile());
        task.getChecksumsFile().set(checksums.toFile());
        task.getOutputFile().set(btmFile.toFile());
        task.getJoernExecutables().setFrom(
                Files.writeString(tempDir.resolve("joern"), ""),
                Files.writeString(tempDir.resolve("joern-parse"), ""),
                Files.writeString(tempDir.resolve("joern-slice"), ""));

        task.analyze();

        String manifestJson = Files.readString(manifest);
        assertThat(manifestJson).contains("\"joernEnabled\": true");
        assertThat(manifestJson).contains("\"joernFingerprint\": \"sha256:semantic\"");
        assertThat(Files.readString(checksums)).contains("joern/cpg.bin");
        assertThat(rowCount(database, "joern_import_run")).isEqualTo(1);
        assertThat(rowCount(database, "semantic_anchor")).isEqualTo(1);
    }

    @Test
    void analyzeTaskRequiresExistingManifestWhenEnabled() {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        var task = project.getTasks().register("analyzeMissingManifest", TestAnalyzeForensicsSemanticsTask.class).get();
        task.getJoernEnabled().set(true);

        assertThatThrownBy(task::analyze)
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("Analysis manifest is missing");
    }

    @Test
    void analyzeTaskRequiresAtLeastOneExistingSourceRoot() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path forensicsDir = Files.createDirectories(tempDir.resolve("build/forensics"));
        Path manifest = forensicsDir.resolve("manifest.json");
        writeManifest(manifest, identity());
        var task = project.getTasks().register("analyzeMissingSources", TestAnalyzeForensicsSemanticsTask.class).get();
        task.getJoernEnabled().set(true);
        task.getManifestFile().set(manifest.toFile());
        task.getSourceRoots().setFrom(tempDir.resolve("missing").toFile());

        assertThatThrownBy(task::analyze)
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("No source roots");
    }

    @Test
    void analyzeTaskRequiresGeneratedBtmFileBeforeUpdatingArtifacts() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path sourceRoot = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path forensicsDir = Files.createDirectories(tempDir.resolve("build/forensics"));
        Path manifest = forensicsDir.resolve("manifest.json");
        Path checksums = forensicsDir.resolve("checksums.sha256");
        BuildIdentity identity = identity();
        writeManifest(manifest, identity);
        Path analysisStoreDirectory = Files.createDirectories(forensicsDir.resolve("analysis-store"));
        H2AnalysisStoreAdapter store = new H2AnalysisStoreAdapter(analysisStoreDirectory.resolve("analysis-store"));
        store.initializeSchema();
        store.createAnalysisRun(identity);

        var task = project.getTasks().register("analyzeMissingBtm", TestAnalyzeForensicsSemanticsTask.class).get();
        task.getJoernEnabled().set(true);
        task.getSourceRoots().setFrom(sourceRoot.toFile());
        task.getAnalysisStoreDirectory().set(analysisStoreDirectory.toFile());
        task.getManifestFile().set(manifest.toFile());
        task.getChecksumsFile().set(checksums.toFile());
        task.getOutputFile().set(forensicsDir.resolve("missing.btm").toFile());

        assertThatThrownBy(task::analyze)
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("Generated BTM file is missing");
    }

    private static void writeManifest(Path manifest, BuildIdentity identity) throws Exception {
        Files.writeString(manifest, """
                {
                  "schemaVersion": "%s",
                  "projectKey": "%s",
                  "analysisRunId": "%s",
                  "buildId": "%s",
                  "sourceFingerprint": "%s",
                  "btmRulesFingerprint": "%s",
                  "pluginVersion": "%s",
                  "joernEnabled": false,
                  "createdAt": "%s",
                  "artifacts": []
                }
                """.formatted(
                identity.schemaVersion().value(),
                identity.projectKey(),
                identity.analysisRunId().value(),
                identity.buildId().value(),
                identity.sourceFingerprint().value(),
                identity.btmRulesFingerprint(),
                identity.pluginVersion(),
                identity.createdAt()));
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

    private static long rowCount(Path databasePath, String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:file:"
                + databasePath.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";DATABASE_TO_UPPER=false");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    public abstract static class TestAnalyzeForensicsSemanticsTask extends AnalyzeForensicsSemanticsTask {
        @Override
        protected SemanticAnalysisPort semanticAnalysisPort(
                JoernAnalysisConfig config,
                ArtifactChecksumService checksumService
        ) {
            return request -> new SemanticAnalysisResult(
                    "joern test",
                    "sha256:semantic",
                    List.of(new ArtifactChecksum("joern/cpg.bin", "joern-cpg", "abc", 3L)),
                    List.of(new SemanticNode("n1", "CALL", "Demo.java", "demo.Demo", "run", "void run()", 12, "call()")),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(new SemanticAnchor(
                            "demo.Demo#run:12:METHOD_ENTER",
                            "n1",
                            "Demo.java",
                            "demo.Demo",
                            "run",
                            "void run()",
                            12,
                            "call()",
                            0.95d,
                            "FQCN_METHOD_LINE_CODE")));
        }
    }
}
