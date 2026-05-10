package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.ForensicsSemanticAnalysisRequest;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Maps Maven-specific Joern parameters to the build-tool-neutral semantic analysis request.
 */
record MavenSemanticAnalysisParameters(
        MavenProject project,
        File sourceRoot,
        boolean includeTests,
        boolean joernEnabled,
        String joernExecutable,
        String joernParseExecutable,
        String joernSliceExecutable,
        File joernWorkspaceDirectory,
        File joernOutputDirectory,
        String joernMaxHeap,
        int joernTimeoutSeconds,
        boolean joernFailOnError,
        File analysisStoreDirectory,
        File manifestFile,
        File checksumsFile,
        File outputFile
) {

    MavenSemanticAnalysisParameters {
        Objects.requireNonNull(project, "project");
    }

    ForensicsSemanticAnalysisRequest toAnalysisRequest() {
        return toAnalysisRequest(new MavenBtmGenParameters(
                project,
                sourceRoot,
                outputFile,
                false,
                null,
                null,
                true,
                analysisStoreDirectory,
                null,
                null,
                null,
                manifestFile,
                checksumsFile,
                false,
                null,
                false,
                false,
                false,
                null,
                null,
                includeTests,
                null,
                true,
                2,
                false
        ).sourceRoots());
    }

    ForensicsSemanticAnalysisRequest toAnalysisRequest(List<Path> sourceRoots) {
        Path buildDirectory = MavenBuildDirectories.buildDirectory(project);
        Path baseDirectory = projectBaseDirectory();
        return new ForensicsSemanticAnalysisRequest(
                joernEnabled,
                resolveExecutable(joernExecutable, "joern", baseDirectory),
                resolveExecutable(joernParseExecutable, "joern-parse", baseDirectory),
                resolveExecutable(joernSliceExecutable, "joern-slice", baseDirectory),
                resolveFile(joernWorkspaceDirectory, buildDirectory.resolve("forensics/joern/workspace")),
                resolveFile(joernOutputDirectory, buildDirectory.resolve("forensics/joern")),
                joernMaxHeap == null ? "" : joernMaxHeap,
                joernTimeoutSeconds,
                joernFailOnError,
                sourceRoots,
                resolveFile(analysisStoreDirectory, buildDirectory.resolve("forensics/analysis-store")),
                resolveFile(manifestFile, buildDirectory.resolve("forensics/manifest.json")),
                resolveFile(checksumsFile, buildDirectory.resolve("forensics/checksums.sha256")),
                resolveFile(outputFile, buildDirectory.resolve("forensics/generated.btm")));
    }

    Path joernOutputDirectoryPath() {
        return resolveFile(
                joernOutputDirectory,
                MavenBuildDirectories.buildDirectory(project).resolve("forensics/joern"));
    }

    private static Path resolveFile(File configuredFile, Path defaultFile) {
        if (configuredFile == null) {
            return defaultFile.toAbsolutePath().normalize();
        }
        return configuredFile.toPath().toAbsolutePath().normalize();
    }

    private Path projectBaseDirectory() {
        return project.getBasedir() == null
                ? Path.of(".").toAbsolutePath().normalize()
                : project.getBasedir().toPath().toAbsolutePath().normalize();
    }

    private static Path resolveExecutable(String configuredExecutable, String defaultCommand, Path baseDirectory) {
        String executable = configuredExecutable == null || configuredExecutable.isBlank()
                ? defaultCommand
                : configuredExecutable.trim();
        Path path = Path.of(executable);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (hasPathSeparator(executable)) {
            return baseDirectory.resolve(path).toAbsolutePath().normalize();
        }
        return path;
    }

    private static boolean hasPathSeparator(String value) {
        return value.contains("/") || value.contains("\\");
    }
}
