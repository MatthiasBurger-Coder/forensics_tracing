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
        boolean profilingEnabled,
        File profileReportFile,
        boolean strictParsing,
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

    MavenBtmGenParameters {
        Objects.requireNonNull(project, "project");
    }

    BtmGenerationRequest toGenerationRequest() {
        Path buildDirectory = buildDirectory(project);
        List<Path> roots = sourceRoots();
        if (roots.isEmpty()) {
            throw new IllegalArgumentException(NO_SOURCE_ROOTS_MESSAGE);
        }
        return BtmGenerationRequest.builder()
                .sourceRoots(roots)
                .outputFile(resolveFile(outputFile, buildDirectory.resolve("forensics/generated.btm")))
                .cacheDatabaseFile(resolveFile(cacheDatabaseFile, buildDirectory.resolve("forensics/cache/scan-cache")))
                .profileReportFile(resolveFile(profileReportFile, buildDirectory.resolve("forensics/scan-profile.json")))
                .cacheEnabled(cacheEnabled)
                .cacheBackend(blankToDefault(cacheBackend, BtmGenerationDefaults.DEFAULT_CACHE_BACKEND))
                .profilingEnabled(profilingEnabled)
                .strictParsing(strictParsing)
                .dependencyAwareInvalidation(dependencyAwareInvalidation)
                .includePackages(csv(includePackages))
                .excludePackages(csv(excludePackages))
                .helperFqn(blankToDefault(helperFqn, BtmGenerationDefaults.DEFAULT_HELPER_FQN))
                .includeEntryExit(includeEntryExit)
                .minBranchesPerMethod(minBranchesPerMethod)
                .includeTimestampHeader(includeTimestampHeader)
                .build();
    }

    private List<Path> sourceRoots() {
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

    private static Path buildDirectory(MavenProject project) {
        Path basedir = project.getBasedir() == null
                ? Path.of(".").toAbsolutePath().normalize()
                : project.getBasedir().toPath().toAbsolutePath().normalize();
        String directory = project.getBuild() == null ? null : project.getBuild().getDirectory();
        if (directory == null || directory.isBlank()) {
            return basedir.resolve("target").toAbsolutePath().normalize();
        }
        Path candidate = Path.of(directory);
        return candidate.isAbsolute()
                ? candidate.normalize()
                : basedir.resolve(candidate).toAbsolutePath().normalize();
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
}
