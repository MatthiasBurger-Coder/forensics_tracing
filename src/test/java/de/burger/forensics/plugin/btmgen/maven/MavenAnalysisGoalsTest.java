package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MavenAnalysisGoalsTest {

    @Test
    void cleanAnalysisDeletesConfiguredArtifacts(@TempDir Path tempDir) throws Exception {
        Path analysisStore = Files.createDirectories(tempDir.resolve("target/forensics/analysis-store"));
        Files.writeString(analysisStore.resolve("analysis-store.mv.db"), "");
        Path manifest = Files.writeString(tempDir.resolve("target/forensics/manifest.json"), "{}");
        Path checksums = Files.writeString(tempDir.resolve("target/forensics/checksums.sha256"), "");
        Path workspace = Files.createDirectories(tempDir.resolve("target/forensics/joern/workspace"));
        Files.writeString(workspace.resolve("cpg.bin"), "");
        Path joernOutput = Files.createDirectories(tempDir.resolve("target/forensics/joern"));
        Files.writeString(joernOutput.resolve("callgraph.json"), "{}");
        CleanForensicsAnalysisMojo mojo = new CleanForensicsAnalysisMojo();
        setField(mojo, "analysisStoreDirectory", analysisStore.toFile());
        setField(mojo, "manifestFile", manifest.toFile());
        setField(mojo, "checksumsFile", checksums.toFile());
        setField(mojo, "joernWorkspaceDirectory", workspace.toFile());
        setField(mojo, "joernOutputDirectory", joernOutput.toFile());

        mojo.execute();

        assertThat(analysisStore).doesNotExist();
        assertThat(manifest).doesNotExist();
        assertThat(checksums).doesNotExist();
        assertThat(joernOutput).doesNotExist();
    }

    @Test
    void cleanAnalysisIgnoresMissingAndNullArtifacts(@TempDir Path tempDir) throws Exception {
        CleanForensicsAnalysisMojo mojo = new CleanForensicsAnalysisMojo();
        setField(mojo, "analysisStoreDirectory", tempDir.resolve("target/forensics/missing-store").toFile());
        setField(mojo, "manifestFile", tempDir.resolve("target/forensics/missing-manifest.json").toFile());
        setField(mojo, "checksumsFile", null);
        setField(mojo, "joernWorkspaceDirectory", tempDir.resolve("target/forensics/missing-workspace").toFile());
        setField(mojo, "joernOutputDirectory", tempDir.resolve("target/forensics/missing-joern").toFile());

        mojo.execute();

        assertThat(tempDir.resolve("target/forensics")).doesNotExist();
    }

    @Test
    void importSemanticsAcceptsExistingCallgraph(@TempDir Path tempDir) throws Exception {
        Path joernOutput = Files.createDirectories(tempDir.resolve("target/forensics/joern"));
        Files.writeString(joernOutput.resolve("callgraph.json"), "{}");
        ImportSemanticsMojo mojo = new ImportSemanticsMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "joernEnabled", true);
        setField(mojo, "joernOutputDirectory", joernOutput.toFile());

        mojo.execute();

        assertThat(joernOutput.resolve("callgraph.json")).exists();
    }

    @Test
    void importSemanticsFailsWhenCallgraphIsMissing(@TempDir Path tempDir) throws Exception {
        ImportSemanticsMojo mojo = new ImportSemanticsMojo();
        setField(mojo, "joernEnabled", true);
        setField(mojo, "joernOutputDirectory", tempDir.resolve("target/forensics/joern").toFile());

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("Joern semantic artifacts are missing");
    }

    @Test
    void analyzeMojoFailsClearlyWhenJoernIsDisabled() {
        AnalyzeMojo mojo = new AnalyzeMojo();

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("joernEnabled=true");
    }

    @Test
    void analyzeMojoRequiresAnalysisStoreWhenJoernIsEnabled() throws Exception {
        AnalyzeMojo mojo = new AnalyzeMojo();
        setField(mojo, "joernEnabled", true);
        setField(mojo, "analysisStoreEnabled", false);

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("Analysis Store is required for forensics:analyze");
    }

    @Test
    void analyzeMojoWrapsGenerationFailuresAfterJoernValidation(@TempDir Path tempDir) throws Exception {
        AnalyzeMojo mojo = new AnalyzeMojo();
        setField(mojo, "project", project(tempDir.resolve("root"), "root"));
        setField(mojo, "joernEnabled", true);
        setField(mojo, "analysisStoreEnabled", true);

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("No existing Maven source roots were found");
    }

    @Test
    void analyzeAggregateMojoFailsClearlyWhenJoernIsDisabled() {
        AnalyzeAggregateMojo mojo = new AnalyzeAggregateMojo();

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("joernEnabled=true");
    }

    @Test
    void analyzeAggregateMojoRequiresAnalysisStoreWhenJoernIsEnabled() throws Exception {
        AnalyzeAggregateMojo mojo = new AnalyzeAggregateMojo();
        setField(mojo, "joernEnabled", true);
        setField(mojo, "analysisStoreEnabled", false);

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("Analysis Store is required for forensics:analyze-aggregate");
    }

    @Test
    void analyzeAggregateMojoWrapsGenerationFailuresAfterJoernValidation(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "root");
        AnalyzeAggregateMojo mojo = new AnalyzeAggregateMojo();
        setField(mojo, "project", root);
        setField(mojo, "session", session(List.of(root)));
        setField(mojo, "joernEnabled", true);
        setField(mojo, "analysisStoreEnabled", true);

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("No existing Maven source roots were found");
    }

    @Test
    void analyzeSemanticsMojoFailsClearlyWhenJoernIsDisabled() {
        AnalyzeSemanticsMojo mojo = new AnalyzeSemanticsMojo();

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertThat(exception.getMessage()).contains("joernEnabled=true");
    }

    @Test
    void semanticRequestUsesDefaultJoernPathsWhenExecutablesAreUnconfigured(@TempDir Path tempDir) {
        BtmGenerationRequest generationRequest = BtmGenerationRequest.builder()
                .sourceRoot(tempDir.resolve("src/main/java"))
                .outputFile(tempDir.resolve("target/forensics/generated.btm"))
                .cacheDatabaseFile(tempDir.resolve("target/forensics/cache/scan-cache"))
                .analysisStoreDirectory(tempDir.resolve("target/forensics/analysis-store"))
                .manifestFile(tempDir.resolve("target/forensics/manifest.json"))
                .checksumsFile(tempDir.resolve("target/forensics/checksums.sha256"))
                .profileReportFile(tempDir.resolve("target/forensics/scan-profile.json"))
                .build();

        var request = MavenForensicsMojoSupport.semanticRequest(
                generationRequest,
                null,
                null,
                null,
                null,
                null,
                30,
                true);

        assertThat(request.joernExecutable().toString()).endsWith(Path.of("joern").toString());
        assertThat(request.joernParseExecutable().toString()).endsWith(Path.of("joern-parse").toString());
        assertThat(request.joernSliceExecutable().toString()).endsWith(Path.of("joern-slice").toString());
        assertThat(request.joernOutputDirectory().toString()).endsWith(Path.of("target/forensics/joern").toString());
    }

    @Test
    void btmAggregateMojoExecutesWithExplicitSourceRoots(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "root");
        Path explicitRoot = createSampleSource(tempDir.resolve("external/java"));
        Path outputFile = tempDir.resolve("root/target/forensics/generated.btm");
        BtmGenAggregateMojo mojo = new BtmGenAggregateMojo();
        mojo.setLog(new SilentLog());
        setField(mojo, "project", root);
        setField(mojo, "session", session(List.of(root)));
        setField(mojo, "sourceRoots", List.of(explicitRoot.toFile()));
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "includePackages", "com.example");
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 2);

        mojo.execute();

        assertThat(Files.readString(outputFile)).contains("com.example.Sample");
    }

    @Test
    void btmAggregateMojoUsesExplicitSourceRootsInsteadOfReactorRoots(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "root");
        MavenProject module = project(tempDir.resolve("root/module"), "module");
        Path reactorRoot = sourceRoot(module, "src/main/java");
        Path explicitRoot = Files.createDirectories(tempDir.resolve("external/java"));
        BtmGenAggregateMojo mojo = new BtmGenAggregateMojo();
        setField(mojo, "project", root);
        setField(mojo, "session", session(List.of(root, module)));
        setField(mojo, "sourceRoots", List.of(explicitRoot.toFile()));

        BtmGenerationRequest request = mojo.parameters().toGenerationRequest();

        assertThat(request.sourceRoots()).containsExactly(explicitRoot.toAbsolutePath().normalize());
        assertThat(request.sourceRoots()).doesNotContain(reactorRoot);
    }

    @Test
    void analyzeAggregateMojoMapsExplicitSourceRootsIntoSharedRequest(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "root");
        Path explicitRoot = Files.createDirectories(tempDir.resolve("external/java"));
        AnalyzeAggregateMojo mojo = new AnalyzeAggregateMojo();
        setField(mojo, "project", root);
        setField(mojo, "session", session(List.of(root)));
        setField(mojo, "sourceRoots", List.of(explicitRoot.toFile()));
        setField(mojo, "analysisStoreEnabled", true);

        BtmGenerationRequest request = mojo.parameters().toGenerationRequest();

        assertThat(request.sourceRoots()).containsExactly(explicitRoot.toAbsolutePath().normalize());
        assertThat(request.analysisStoreEnabled()).isTrue();
    }

    private static MavenSession session(List<MavenProject> projects) {
        MavenSession session = mock(MavenSession.class);
        when(session.getProjects()).thenReturn(projects);
        return session;
    }

    private static MavenProject project(Path projectDirectory, String artifactId) throws Exception {
        Files.createDirectories(projectDirectory);
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        MavenProject project = new MavenProject(model);
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(projectDirectory.resolve("target").toString());
        project.setBuild(build);
        return project;
    }

    private static Path sourceRoot(MavenProject project, String relativePath) throws Exception {
        Path root = Files.createDirectories(project.getBasedir().toPath().resolve(relativePath));
        project.addCompileSourceRoot(root.toString());
        return root.toAbsolutePath().normalize();
    }

    private static Path createSampleSource(Path sourceRoot) throws Exception {
        Path packageDirectory = sourceRoot.resolve("com/example");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("Sample.java"), """
                package com.example;
                public class Sample {
                  public int run(int value) {
                    if (value > 0) { }
                    return value;
                  }
                }
                """);
        return sourceRoot;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class SilentLog implements Log {
        @Override public boolean isDebugEnabled() { return true; }
        @Override public void debug(CharSequence content) { }
        @Override public void debug(CharSequence content, Throwable error) { }
        @Override public void debug(Throwable error) { }
        @Override public boolean isInfoEnabled() { return true; }
        @Override public void info(CharSequence content) { }
        @Override public void info(CharSequence content, Throwable error) { }
        @Override public void info(Throwable error) { }
        @Override public boolean isWarnEnabled() { return true; }
        @Override public void warn(CharSequence content) { }
        @Override public void warn(CharSequence content, Throwable error) { }
        @Override public void warn(Throwable error) { }
        @Override public boolean isErrorEnabled() { return true; }
        @Override public void error(CharSequence content) { }
        @Override public void error(CharSequence content, Throwable error) { }
        @Override public void error(Throwable error) { }
    }
}
