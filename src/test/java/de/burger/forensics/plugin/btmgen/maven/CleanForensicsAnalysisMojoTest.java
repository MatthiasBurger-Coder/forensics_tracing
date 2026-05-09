package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CleanForensicsAnalysisMojoTest {

    @Test
    void deletesGeneratedAnalysisArtifacts(@TempDir Path tempDir) throws Exception {
        Path outputFile = writeFile(tempDir.resolve("target/forensics/generated.btm"));
        Path manifestFile = writeFile(tempDir.resolve("target/forensics/manifest.json"));
        Path checksumsFile = writeFile(tempDir.resolve("target/forensics/checksums.sha256"));
        Path analysisStore = writeFile(tempDir.resolve("target/forensics/analysis-store/analysis-store.mv.db")).getParent();
        Path joernOutput = writeFile(tempDir.resolve("target/forensics/joern/callgraph.json")).getParent();
        Path joernWorkspace = writeFile(tempDir.resolve("target/forensics/joern/workspace/cpg.bin")).getParent();
        Path unrelated = writeFile(tempDir.resolve("target/forensics/keep.txt"));

        CleanForensicsAnalysisMojo mojo = new CleanForensicsAnalysisMojo();
        setField(mojo, "project", projectWithBuildDirectory(tempDir));

        mojo.execute();

        assertThat(outputFile).doesNotExist();
        assertThat(manifestFile).doesNotExist();
        assertThat(checksumsFile).doesNotExist();
        assertThat(analysisStore).doesNotExist();
        assertThat(joernOutput).doesNotExist();
        assertThat(joernWorkspace).doesNotExist();
        assertThat(unrelated).exists();
    }

    @Test
    void deletesConfiguredAnalysisArtifacts(@TempDir Path tempDir) throws Exception {
        Path outputFile = writeFile(tempDir.resolve("custom/rules.btm"));
        Path manifestFile = writeFile(tempDir.resolve("custom/manifest.json"));
        Path checksumsFile = writeFile(tempDir.resolve("custom/checksums.sha256"));
        Path analysisStore = writeFile(tempDir.resolve("custom/analysis-store/analysis-store.mv.db")).getParent();
        Path joernOutput = writeFile(tempDir.resolve("custom/joern/callgraph.json")).getParent();
        Path joernWorkspace = writeFile(tempDir.resolve("custom/joern-workspace/cpg.bin")).getParent();

        CleanForensicsAnalysisMojo mojo = new CleanForensicsAnalysisMojo();
        setField(mojo, "project", projectWithBuildDirectory(tempDir));
        setField(mojo, "outputFile", outputFile.toFile());
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "checksumsFile", checksumsFile.toFile());
        setField(mojo, "analysisStoreDirectory", analysisStore.toFile());
        setField(mojo, "joernOutputDirectory", joernOutput.toFile());
        setField(mojo, "joernWorkspaceDirectory", joernWorkspace.toFile());

        mojo.execute();

        assertThat(outputFile).doesNotExist();
        assertThat(manifestFile).doesNotExist();
        assertThat(checksumsFile).doesNotExist();
        assertThat(analysisStore).doesNotExist();
        assertThat(joernOutput).doesNotExist();
        assertThat(joernWorkspace).doesNotExist();
    }

    private static Path writeFile(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "content");
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

    private static void setField(CleanForensicsAnalysisMojo mojo, String name, Object value) throws Exception {
        Field field = CleanForensicsAnalysisMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }
}
