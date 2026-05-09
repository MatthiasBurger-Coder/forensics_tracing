package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationDefaults;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenBtmGenParametersTest {

    @Test
    void mapsMavenProjectRootsAndParametersToGenerationRequest(@TempDir Path tempDir) throws Exception {
        Path mainRoot = createDirectory(tempDir.resolve("src/main/java"));
        Path testRoot = createDirectory(tempDir.resolve("src/test/java"));
        MavenProject project = projectWithBuildDirectory(tempDir);
        project.addCompileSourceRoot(mainRoot.toString());
        project.addTestCompileSourceRoot(testRoot.toString());
        MavenBtmGenParameters parameters = new MavenBtmGenParameters(
                project,
                List.of(),
                null,
                List.of(),
                null,
                true,
                " ",
                null,
                true,
                null,
                " ",
                " ",
                " ",
                null,
                null,
                true,
                null,
                true,
                true,
                false,
                "com.example, org.demo, com.example",
                "com.skip, , org.skip",
                true,
                " ",
                false,
                3,
                true
        );

        BtmGenerationRequest request = parameters.toGenerationRequest();

        assertThat(request.sourceRoots()).containsExactly(mainRoot.toAbsolutePath(), testRoot.toAbsolutePath());
        assertThat(request.outputFile()).isEqualTo(tempDir.resolve("target/forensics/generated.btm").toAbsolutePath());
        assertThat(request.cacheDatabaseFile()).isEqualTo(tempDir.resolve("target/forensics/cache/scan-cache").toAbsolutePath());
        assertThat(request.analysisStoreEnabled()).isTrue();
        assertThat(request.analysisStoreDirectory()).isEqualTo(tempDir.resolve("target/forensics/analysis-store").toAbsolutePath());
        assertThat(request.cleanupPolicy()).isEqualTo(BtmGenerationDefaults.defaultCleanupPolicy());
        assertThat(request.projectKey()).isEqualTo("de.burger.forensics:sample");
        assertThat(request.pluginVersion()).isEqualTo("1.0.0");
        assertThat(request.manifestFile()).isEqualTo(tempDir.resolve("target/forensics/manifest.json").toAbsolutePath());
        assertThat(request.checksumsFile()).isEqualTo(tempDir.resolve("target/forensics/checksums.sha256").toAbsolutePath());
        assertThat(request.profileReportFile()).isEqualTo(tempDir.resolve("target/forensics/scan-profile.json").toAbsolutePath());
        assertThat(request.cacheEnabled()).isTrue();
        assertThat(request.cacheBackend()).isEqualTo(BtmGenerationDefaults.DEFAULT_CACHE_BACKEND);
        assertThat(request.profilingEnabled()).isTrue();
        assertThat(request.strictParsing()).isTrue();
        assertThat(request.strictConditionValidation()).isTrue();
        assertThat(request.includePackages()).containsExactly("com.example", "org.demo");
        assertThat(request.excludePackages()).containsExactly("com.skip", "org.skip");
        assertThat(request.helperFqn()).isEqualTo(BtmGenerationDefaults.DEFAULT_HELPER_FQN);
        assertThat(request.includeEntryExit()).isFalse();
        assertThat(request.minBranchesPerMethod()).isEqualTo(3);
        assertThat(request.includeTimestampHeader()).isTrue();
    }

    @Test
    void explicitSourceRootsCombineAndOverrideProjectRoots(@TempDir Path tempDir) throws Exception {
        Path mainRoot = createDirectory(tempDir.resolve("src/main/java"));
        Path explicitRoot = createDirectory(tempDir.resolve("external/java"));
        Path additionalExplicitRoot = createDirectory(tempDir.resolve("generated/java"));
        MavenProject project = projectWithBuildDirectory(tempDir);
        project.addCompileSourceRoot(mainRoot.toString());
        MavenBtmGenParameters parameters = new MavenBtmGenParameters(
                project,
                List.of(),
                explicitRoot.toFile(),
                List.of(additionalExplicitRoot.toFile(), explicitRoot.toFile()),
                tempDir.resolve("custom/rules.btm").toFile(),
                false,
                "h2",
                tempDir.resolve("custom/cache/scan-cache").toFile(),
                true,
                tempDir.resolve("custom/store").toFile(),
                "DELETE_ON_FAILURE",
                "custom-key",
                "custom-version",
                tempDir.resolve("custom/manifest.json").toFile(),
                tempDir.resolve("custom/checksums.sha256").toFile(),
                false,
                tempDir.resolve("custom/profile.json").toFile(),
                false,
                false,
                false,
                "",
                "",
                true,
                BtmGenerationDefaults.DEFAULT_HELPER_FQN,
                true,
                2,
                false
        );

        BtmGenerationRequest request = parameters.toGenerationRequest();

        assertThat(request.sourceRoots()).containsExactly(
                explicitRoot.toAbsolutePath(),
                additionalExplicitRoot.toAbsolutePath());
        assertThat(request.outputFile()).isEqualTo(tempDir.resolve("custom/rules.btm").toAbsolutePath());
        assertThat(request.cacheDatabaseFile()).isEqualTo(tempDir.resolve("custom/cache/scan-cache").toAbsolutePath());
        assertThat(request.analysisStoreDirectory()).isEqualTo(tempDir.resolve("custom/store").toAbsolutePath());
        assertThat(request.cleanupPolicy()).isEqualTo("DELETE_ON_FAILURE");
        assertThat(request.projectKey()).isEqualTo("custom-key");
        assertThat(request.pluginVersion()).isEqualTo("custom-version");
        assertThat(request.manifestFile()).isEqualTo(tempDir.resolve("custom/manifest.json").toAbsolutePath());
        assertThat(request.checksumsFile()).isEqualTo(tempDir.resolve("custom/checksums.sha256").toAbsolutePath());
        assertThat(request.profileReportFile()).isEqualTo(tempDir.resolve("custom/profile.json").toAbsolutePath());
    }

    @Test
    void rejectsProjectsWithoutExistingSourceRoots(@TempDir Path tempDir) {
        MavenProject project = projectWithBuildDirectory(tempDir);
        MavenBtmGenParameters parameters = new MavenBtmGenParameters(
                project,
                List.of(),
                null,
                List.of(),
                null,
                false,
                "h2",
                null,
                false,
                null,
                "KEEP_ON_SUCCESS",
                "",
                "",
                null,
                null,
                false,
                null,
                false,
                false,
                false,
                "",
                "",
                false,
                BtmGenerationDefaults.DEFAULT_HELPER_FQN,
                true,
                2,
                false
        );

        assertThatThrownBy(parameters::toGenerationRequest)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existing Maven source roots were found");
    }

    @Test
    void mapsRegularFileSourceRootAndDefaultTargetWhenProjectHasNoBuild(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("Single.java");
        Files.writeString(sourceFile, "class Single {}");
        MavenProject project = new MavenProject(new Model());
        project.setBuild(null);
        project.addCompileSourceRoot(sourceFile.toString());
        project.addCompileSourceRoot(tempDir.resolve("missing").toString());
        MavenBtmGenParameters parameters = defaultParameters(project);

        BtmGenerationRequest request = parameters.toGenerationRequest();

        assertThat(request.sourceRoots()).containsExactly(sourceFile.toAbsolutePath());
        assertThat(request.outputFile().toString()).endsWith(Path.of("target/forensics/generated.btm").toString());
    }

    @Test
    void mapsBlankBuildDirectoryToProjectTargetDirectory(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = createDirectory(tempDir.resolve("src/main/java"));
        MavenProject project = projectWithBlankBuildDirectory(tempDir);
        project.addCompileSourceRoot(sourceRoot.toString());
        MavenBtmGenParameters parameters = defaultParameters(project);

        BtmGenerationRequest request = parameters.toGenerationRequest();

        assertThat(request.outputFile()).isEqualTo(tempDir.resolve("target/forensics/generated.btm").toAbsolutePath());
    }

    @Test
    void mapsRelativeBuildDirectoryAgainstProjectBaseDirectory(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = createDirectory(tempDir.resolve("src/main/java"));
        MavenProject project = projectWithRelativeBuildDirectory(tempDir);
        project.addCompileSourceRoot(sourceRoot.toString());
        MavenBtmGenParameters parameters = defaultParameters(project);

        BtmGenerationRequest request = parameters.toGenerationRequest();

        assertThat(request.outputFile()).isEqualTo(tempDir.resolve("target/custom/forensics/generated.btm").toAbsolutePath());
    }

    @Test
    void mapsPartialProjectIdentityToStableProjectKeys(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = createDirectory(tempDir.resolve("src/main/java"));
        MavenProject unknownProject = projectWithIdentity(tempDir.resolve("unknown"), null, null);
        unknownProject.addCompileSourceRoot(sourceRoot.toString());
        MavenProject artifactOnlyProject = projectWithIdentity(tempDir.resolve("artifact-only"), null, "sample");
        artifactOnlyProject.addCompileSourceRoot(sourceRoot.toString());
        MavenProject groupOnlyProject = projectWithIdentity(tempDir.resolve("group-only"), "de.burger.forensics", null);
        groupOnlyProject.addCompileSourceRoot(sourceRoot.toString());

        assertThat(defaultParameters(unknownProject).toGenerationRequest().projectKey()).isEqualTo("UNKNOWN");
        assertThat(defaultParameters(artifactOnlyProject).toGenerationRequest().projectKey()).isEqualTo("sample");
        assertThat(defaultParameters(groupOnlyProject).toGenerationRequest().projectKey()).isEqualTo("de.burger.forensics");
    }

    private static Path createDirectory(Path path) throws Exception {
        Files.createDirectories(path);
        return path;
    }

    private static MavenProject projectWithBuildDirectory(Path projectDirectory) {
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId("sample");
        model.setVersion("1.0.0");
        MavenProject project = new MavenProject(model);
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(projectDirectory.resolve("target").toString());
        project.setBuild(build);
        return project;
    }

    private static MavenProject projectWithIdentity(Path projectDirectory, String groupId, String artifactId) throws Exception {
        Files.createDirectories(projectDirectory);
        Model model = new Model();
        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        MavenProject project = new MavenProject(model);
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(projectDirectory.resolve("target").toString());
        project.setBuild(build);
        return project;
    }

    private static MavenProject projectWithBlankBuildDirectory(Path projectDirectory) {
        MavenProject project = projectWithBuildDirectory(projectDirectory);
        Build build = new Build();
        build.setDirectory(" ");
        project.setBuild(build);
        return project;
    }

    private static MavenProject projectWithRelativeBuildDirectory(Path projectDirectory) {
        MavenProject project = projectWithBuildDirectory(projectDirectory);
        Build build = new Build();
        build.setDirectory("target/custom");
        project.setBuild(build);
        return project;
    }

    private static MavenBtmGenParameters defaultParameters(MavenProject project) {
        return new MavenBtmGenParameters(
                project,
                List.of(),
                null,
                List.of(),
                null,
                false,
                "h2",
                null,
                false,
                null,
                "KEEP_ON_SUCCESS",
                "",
                "",
                null,
                null,
                false,
                null,
                false,
                false,
                false,
                "",
                "",
                false,
                BtmGenerationDefaults.DEFAULT_HELPER_FQN,
                true,
                2,
                false
        );
    }
}
