package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationDefaults;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Maps Maven-specific plugin parameters to the build-tool-neutral generation request.
 */
record MavenBtmGenParameters(
        MavenProject project,
        File sourceRoot,
        File outputFile,
        boolean cacheEnabled,
        String cacheBackend,
        File cacheDatabaseFile,
        boolean analysisStoreEnabled,
        File analysisStoreDirectory,
        String cleanupPolicy,
        String projectKey,
        String pluginVersion,
        File manifestFile,
        File checksumsFile,
        boolean profilingEnabled,
        File profileReportFile,
        boolean strictParsing,
        boolean strictConditionValidation,
        boolean dependencyAwareInvalidation,
        String includePackages,
        String excludePackages,
        boolean includeTests,
        String helperFqn,
        boolean includeEntryExit,
        int minBranchesPerMethod,
        boolean includeTimestampHeader
) {

    private static final String NO_SOURCE_ROOTS_MESSAGE =
            "No existing Maven source roots were found. Configure forensics.sourceRoot or add compile source roots.";
    private static final String UNKNOWN_PROJECT_VALUE = "UNKNOWN";

    MavenBtmGenParameters {
        Objects.requireNonNull(project, "project");
    }

    BtmGenerationRequest toGenerationRequest() {
        return toGenerationRequest(sourceRoots());
    }

    BtmGenerationRequest toGenerationRequest(List<Path> roots) {
        Path buildDirectory = MavenBuildDirectories.buildDirectory(project);
        if (roots.isEmpty()) {
            throw new IllegalArgumentException(NO_SOURCE_ROOTS_MESSAGE);
        }
        return BtmGenerationRequest.builder()
                .sourceRoots(roots)
                .outputFile(resolveFile(outputFile, buildDirectory.resolve("forensics/generated.btm")))
                .cacheDatabaseFile(resolveFile(cacheDatabaseFile, buildDirectory.resolve("forensics/cache/scan-cache")))
                .analysisStoreDirectory(resolveFile(
                        analysisStoreDirectory,
                        buildDirectory.resolve("forensics/analysis-store")))
                .profileReportFile(resolveFile(profileReportFile, buildDirectory.resolve("forensics/scan-profile.json")))
                .cacheEnabled(cacheEnabled)
                .analysisStoreEnabled(analysisStoreEnabled)
                .cacheBackend(blankToDefault(cacheBackend, BtmGenerationDefaults.DEFAULT_CACHE_BACKEND))
                .profilingEnabled(profilingEnabled)
                .strictParsing(strictParsing)
                .strictConditionValidation(strictConditionValidation)
                .dependencyAwareInvalidation(dependencyAwareInvalidation)
                .includePackages(csv(includePackages))
                .excludePackages(csv(excludePackages))
                .helperFqn(blankToDefault(helperFqn, BtmGenerationDefaults.DEFAULT_HELPER_FQN))
                .includeEntryExit(includeEntryExit)
                .minBranchesPerMethod(minBranchesPerMethod)
                .includeTimestampHeader(includeTimestampHeader)
                .cleanupPolicy(blankToDefault(cleanupPolicy, BtmGenerationDefaults.defaultCleanupPolicy()))
                .projectKey(blankToDefault(projectKey, defaultProjectKey(project)))
                .pluginVersion(blankToDefault(pluginVersion, defaultPluginVersion(project)))
                .manifestFile(resolveFile(manifestFile, buildDirectory.resolve("forensics/manifest.json")))
                .checksumsFile(resolveFile(checksumsFile, buildDirectory.resolve("forensics/checksums.sha256")))
                .build();
    }

    List<Path> sourceRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        if (sourceRoot != null) {
            addExistingRoot(roots, sourceRoot.toPath());
            return List.copyOf(roots);
        }
        project.getCompileSourceRoots().forEach(root -> addExistingRoot(roots, Path.of(root)));
        if (includeTests) {
            project.getTestCompileSourceRoots().forEach(root -> addExistingRoot(roots, Path.of(root)));
        }
        return List.copyOf(roots);
    }

    private static void addExistingRoot(LinkedHashSet<Path> roots, Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (Files.exists(normalized) && (Files.isDirectory(normalized) || Files.isRegularFile(normalized))) {
            roots.add(normalized);
        }
    }

    private static Path resolveFile(File configuredFile, Path defaultFile) {
        if (configuredFile == null) {
            return defaultFile.toAbsolutePath().normalize();
        }
        return configuredFile.toPath().toAbsolutePath().normalize();
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .distinct()
                .toList();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String defaultProjectKey(MavenProject project) {
        String groupId = blankToDefault(project.getGroupId(), UNKNOWN_PROJECT_VALUE);
        String artifactId = blankToDefault(project.getArtifactId(), UNKNOWN_PROJECT_VALUE);
        return groupId + ":" + artifactId;
    }

    private static String defaultPluginVersion(MavenProject project) {
        return blankToDefault(project.getVersion(), UNKNOWN_PROJECT_VALUE);
    }
}
