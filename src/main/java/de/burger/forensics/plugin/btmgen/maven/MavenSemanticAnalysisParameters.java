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
        File joernExecutable,
        File joernParseExecutable,
        File joernSliceExecutable,
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
        return new ForensicsSemanticAnalysisRequest(
                joernEnabled,
                resolveFile(joernExecutable, Path.of("joern")),
                resolveFile(joernParseExecutable, Path.of("joern-parse")),
                resolveFile(joernSliceExecutable, Path.of("joern-slice")),
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
}
