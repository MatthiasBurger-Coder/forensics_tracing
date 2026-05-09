package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Maven goal that deletes generated forensics analysis artifacts.
 */
@Mojo(
        name = "clean-analysis",
        requiresProject = true,
        threadSafe = true
)
public class CleanForensicsAnalysisMojo extends AbstractMojo {

    @Parameter(property = "forensics.analysisStoreDirectory", defaultValue = "${project.build.directory}/forensics/analysis-store")
    private File analysisStoreDirectory;

    @Parameter(property = "forensics.manifestFile", defaultValue = "${project.build.directory}/forensics/manifest.json")
    private File manifestFile;

    @Parameter(property = "forensics.checksumsFile", defaultValue = "${project.build.directory}/forensics/checksums.sha256")
    private File checksumsFile;

    @Parameter(property = "forensics.joernWorkspaceDirectory", defaultValue = "${project.build.directory}/forensics/joern/workspace")
    private File joernWorkspaceDirectory;

    @Parameter(property = "forensics.joernOutputDirectory", defaultValue = "${project.build.directory}/forensics/joern")
    private File joernOutputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        for (File artifact : Arrays.asList(
                analysisStoreDirectory,
                manifestFile,
                checksumsFile,
                joernWorkspaceDirectory,
                joernOutputDirectory)) {
            deleteIfConfigured(artifact);
        }
    }

    private static void deleteIfConfigured(File artifact) throws MojoExecutionException {
        if (artifact == null) {
            return;
        }
        try {
            deleteRecursively(artifact.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to delete forensics artifact " + artifact + ".", e);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isRegularFile(path)) {
            Files.deleteIfExists(path);
            return;
        }
        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path candidate : paths) {
                Files.deleteIfExists(candidate);
            }
        }
    }
}
