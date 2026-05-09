package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Deletes generated Maven forensics analysis artifacts.
 */
@Mojo(
        name = "clean-analysis",
        requiresProject = true,
        threadSafe = true
)
public class CleanForensicsAnalysisMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forensics.outputFile", defaultValue = "${project.build.directory}/forensics/generated.btm")
    private File outputFile;

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
        try {
            for (Path target : cleanupTargets()) {
                deleteIfExists(target);
            }
        } catch (UncheckedIOException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception.getCause());
        }
    }

    private List<Path> cleanupTargets() {
        Path buildDirectory = MavenBuildDirectories.buildDirectory(project);
        return List.of(
                resolveFile(outputFile, buildDirectory.resolve("forensics/generated.btm")),
                resolveFile(manifestFile, buildDirectory.resolve("forensics/manifest.json")),
                resolveFile(checksumsFile, buildDirectory.resolve("forensics/checksums.sha256")),
                resolveFile(analysisStoreDirectory, buildDirectory.resolve("forensics/analysis-store")),
                resolveFile(joernOutputDirectory, buildDirectory.resolve("forensics/joern")),
                resolveFile(joernWorkspaceDirectory, buildDirectory.resolve("forensics/joern/workspace"))
        );
    }

    private static Path resolveFile(File configuredFile, Path defaultFile) {
        if (configuredFile == null) {
            return defaultFile.toAbsolutePath().normalize();
        }
        return configuredFile.toPath().toAbsolutePath().normalize();
    }

    private static void deleteIfExists(Path target) {
        if (!Files.exists(target)) {
            return;
        }
        try {
            if (Files.isDirectory(target)) {
                try (var stream = Files.walk(target)) {
                    stream.sorted(Comparator.reverseOrder())
                            .forEach(CleanForensicsAnalysisMojo::deletePath);
                }
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete generated forensics artifact " + target + ".", exception);
        }
    }

    private static void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete generated forensics artifact " + path + ".", exception);
        }
    }
}
