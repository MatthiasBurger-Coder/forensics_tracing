package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenReactorSourceRootCollectorTest {

    @Test
    void collectsExistingMainRootsFromReactorModules(@TempDir Path tempDir) throws Exception {
        MavenProject root = project("root", "pom", tempDir);
        MavenProject moduleA = project("module-a", "jar", tempDir.resolve("module-a"));
        MavenProject moduleB = project("module-b", "jar", tempDir.resolve("module-b"));
        MavenProject emptyModule = project("module-empty", "jar", tempDir.resolve("module-empty"));
        Path moduleARoot = createDirectory(tempDir.resolve("module-a/src/main/java"));
        Path moduleBRoot = createDirectory(tempDir.resolve("module-b/src/main/java"));
        moduleA.addCompileSourceRoot(moduleARoot.toString());
        moduleA.addCompileSourceRoot(moduleARoot.toString());
        moduleB.addCompileSourceRoot(moduleBRoot.toString());
        emptyModule.addCompileSourceRoot(tempDir.resolve("module-empty/src/main/java").toString());

        List<Path> roots = new MavenReactorSourceRootCollector().collect(
                List.of(moduleB, emptyModule, root, moduleA),
                false);

        assertThat(roots).containsExactly(moduleARoot.toAbsolutePath(), moduleBRoot.toAbsolutePath());
    }

    @Test
    void optionallyCollectsTestSourceRoots(@TempDir Path tempDir) throws Exception {
        MavenProject module = project("module", "jar", tempDir.resolve("module"));
        Path mainRoot = createDirectory(tempDir.resolve("module/src/main/java"));
        Path testRoot = createDirectory(tempDir.resolve("module/src/test/java"));
        module.addCompileSourceRoot(mainRoot.toString());
        module.addTestCompileSourceRoot(testRoot.toString());

        List<Path> roots = new MavenReactorSourceRootCollector().collect(List.of(module), true);

        assertThat(roots).containsExactly(mainRoot.toAbsolutePath(), testRoot.toAbsolutePath());
    }

    @Test
    void collectsRegularFileRootsFromProjectsWithoutBaseDirectory(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("Single.java");
        Files.writeString(sourceFile, "class Single {}");
        MavenProject project = projectWithoutBaseDirectory("single-file-module");
        project.addCompileSourceRoot(sourceFile.toString());

        List<Path> roots = new MavenReactorSourceRootCollector().collect(Arrays.asList(null, project), false);

        assertThat(roots).containsExactly(sourceFile.toAbsolutePath());
    }

    private static Path createDirectory(Path path) throws Exception {
        Files.createDirectories(path);
        return path;
    }

    private static MavenProject project(String artifactId, String packaging, Path basedir) {
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        model.setPackaging(packaging);
        MavenProject project = new MavenProject(model);
        project.setFile(basedir.resolve("pom.xml").toFile());
        return project;
    }

    private static MavenProject projectWithoutBaseDirectory(String artifactId) {
        Model model = new Model();
        model.setArtifactId(artifactId);
        return new MavenProject(model);
    }
}
