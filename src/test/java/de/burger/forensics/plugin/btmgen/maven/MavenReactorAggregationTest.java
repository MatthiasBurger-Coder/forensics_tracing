package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MavenReactorAggregationTest {

    @Test
    void collectorIgnoresRootPomWithoutSourcesAndAggregatesReactorModules(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "pom", "root");
        MavenProject moduleA = project(tempDir.resolve("root/module-a"), "jar", "module-a");
        MavenProject moduleB = project(tempDir.resolve("root/module-b"), "jar", "module-b");
        MavenProject emptyModule = project(tempDir.resolve("root/module-empty"), "jar", "module-empty");
        Path moduleARoot = sourceRoot(moduleA, "src/main/java");
        Path moduleBRoot = sourceRoot(moduleB, "src/main/java");
        MavenSession session = session(List.of(moduleB, emptyModule, root, moduleA));

        List<Path> roots = new MavenReactorSourceRootCollector().collect(session, root, false);

        assertThat(roots).containsExactly(moduleARoot, moduleBRoot);
    }

    @Test
    void collectorIncludesTestRootsWhenRequested(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "pom", "root");
        MavenProject module = project(tempDir.resolve("root/module"), "jar", "module");
        Path mainRoot = sourceRoot(module, "src/main/java");
        Path testRoot = testSourceRoot(module, "src/test/java");
        MavenSession session = session(List.of(root, module));

        List<Path> roots = new MavenReactorSourceRootCollector().collect(session, root, true);

        assertThat(roots).containsExactly(mainRoot, testRoot);
    }

    @Test
    void collectorFallsBackToRootProjectWhenSessionIsMissing(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "jar", "root");
        Path mainRoot = sourceRoot(root, "src/main/java");

        List<Path> roots = new MavenReactorSourceRootCollector().collect(null, root, false);

        assertThat(roots).containsExactly(mainRoot);
    }

    @Test
    void collectorFallsBackToRootProjectWhenSessionProjectsAreMissing(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "jar", "root");
        Path mainRoot = sourceRoot(root, "src/main/java");
        MavenSession session = mock(MavenSession.class);
        when(session.getProjects()).thenReturn(null);

        List<Path> roots = new MavenReactorSourceRootCollector().collect(session, root, false);

        assertThat(roots).containsExactly(mainRoot);
    }

    @Test
    void collectorFallsBackToRootProjectWhenSessionProjectsAreEmpty(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "jar", "root");
        Path mainRoot = sourceRoot(root, "src/main/java");

        List<Path> roots = new MavenReactorSourceRootCollector().collect(session(List.of()), root, false);

        assertThat(roots).containsExactly(mainRoot);
    }

    @Test
    void collectorSortsProjectsWithoutBaseDirectoriesByCoordinates(@TempDir Path tempDir) throws Exception {
        Path unknownRoot = Files.createDirectories(tempDir.resolve("unknown/src/main/java"));
        Path groupOnlyRoot = Files.createDirectories(tempDir.resolve("group-only/src/main/java"));
        Path fullRoot = Files.createDirectories(tempDir.resolve("full/src/main/java"));
        MavenProject unknownProject = projectWithoutBaseDirectory(null, null, unknownRoot);
        MavenProject groupOnlyProject = projectWithoutBaseDirectory("de.burger.forensics", null, groupOnlyRoot);
        MavenProject fullProject = projectWithoutBaseDirectory("de.burger.forensics", "full", fullRoot);
        MavenProject root = project(tempDir.resolve("root"), "pom", "root");

        List<Path> roots = new MavenReactorSourceRootCollector()
                .collect(session(List.of(fullProject, groupOnlyProject, unknownProject)), root, false);

        assertThat(roots).containsExactly(
                unknownRoot.toAbsolutePath().normalize(),
                groupOnlyRoot.toAbsolutePath().normalize(),
                fullRoot.toAbsolutePath().normalize());
    }

    @Test
    void collectorAddsRegularFileRootsAndSkipsMissingRoots(@TempDir Path tempDir) throws Exception {
        Path sourceFile = Files.writeString(tempDir.resolve("Single.java"), "class Single {}");
        MavenProject root = project(tempDir.resolve("root"), "jar", "root");
        root.addCompileSourceRoot(tempDir.resolve("missing").toString());
        root.addCompileSourceRoot(sourceFile.toString());

        List<Path> roots = new MavenReactorSourceRootCollector().collect(null, root, false);

        assertThat(roots).containsExactly(sourceFile.toAbsolutePath().normalize());
    }

    @Test
    void aggregateMojoMapsReactorRootsIntoSharedRequest(@TempDir Path tempDir) throws Exception {
        MavenProject root = project(tempDir.resolve("root"), "pom", "root");
        MavenProject module = project(tempDir.resolve("root/module"), "jar", "module");
        Path mainRoot = sourceRoot(module, "src/main/java");
        BtmGenAggregateMojo mojo = new BtmGenAggregateMojo();
        setField(mojo, "project", root);
        setField(mojo, "session", session(List.of(root, module)));
        setField(mojo, "outputFile", tempDir.resolve("root/target/forensics/generated.btm").toFile());
        setField(mojo, "cacheDatabaseFile", tempDir.resolve("root/target/forensics/cache/scan-cache").toFile());
        setField(mojo, "analysisStoreEnabled", false);
        setField(mojo, "profileReportFile", tempDir.resolve("root/target/forensics/scan-profile.json").toFile());
        setField(mojo, "includeEntryExit", true);
        setField(mojo, "minBranchesPerMethod", 2);

        BtmGenerationRequest request = mojo.parameters().toGenerationRequest();

        assertThat(request.sourceRoots()).containsExactly(mainRoot);
        assertThat(request.projectKey()).isEqualTo("de.burger.forensics:root");
        assertThat(request.analysisStoreEnabled()).isFalse();
    }

    private static MavenSession session(List<MavenProject> projects) {
        MavenSession session = mock(MavenSession.class);
        when(session.getProjects()).thenReturn(projects);
        return session;
    }

    private static MavenProject project(Path projectDirectory, String packaging, String artifactId) throws Exception {
        Files.createDirectories(projectDirectory);
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        model.setPackaging(packaging);
        MavenProject project = new MavenProject(model);
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(projectDirectory.resolve("target").toString());
        project.setBuild(build);
        return project;
    }

    private static MavenProject projectWithoutBaseDirectory(String groupId, String artifactId, Path sourceRoot) {
        Model model = new Model();
        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        MavenProject project = new MavenProject(model);
        project.addCompileSourceRoot(sourceRoot.toString());
        return project;
    }

    private static Path sourceRoot(MavenProject project, String relativePath) throws Exception {
        Path root = Files.createDirectories(project.getBasedir().toPath().resolve(relativePath));
        project.addCompileSourceRoot(root.toString());
        return root.toAbsolutePath().normalize();
    }

    private static Path testSourceRoot(MavenProject project, String relativePath) throws Exception {
        Path root = Files.createDirectories(project.getBasedir().toPath().resolve(relativePath));
        project.addTestCompileSourceRoot(root.toString());
        return root.toAbsolutePath().normalize();
    }

    private static void setField(BtmGenAggregateMojo mojo, String name, Object value) throws Exception {
        Field field = BtmGenAggregateMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }
}
