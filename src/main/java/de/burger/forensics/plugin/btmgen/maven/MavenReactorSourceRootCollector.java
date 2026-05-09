package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.project.MavenProject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Collects existing Java source roots from a Maven reactor without rendering or scanning them.
 */
final class MavenReactorSourceRootCollector {

    List<Path> collect(Collection<MavenProject> reactorProjects, boolean includeTests) {
        Objects.requireNonNull(reactorProjects, "reactorProjects");
        Set<Path> roots = new LinkedHashSet<>();
        reactorProjects.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MavenReactorSourceRootCollector::projectSortKey))
                .forEach(project -> collectProjectRoots(project, includeTests, roots));
        return List.copyOf(roots);
    }

    private static void collectProjectRoots(MavenProject project, boolean includeTests, Set<Path> roots) {
        project.getCompileSourceRoots().forEach(root -> addExistingRoot(roots, Path.of(root)));
        if (includeTests) {
            project.getTestCompileSourceRoots().forEach(root -> addExistingRoot(roots, Path.of(root)));
        }
    }

    private static void addExistingRoot(Set<Path> roots, Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (Files.exists(normalized) && (Files.isDirectory(normalized) || Files.isRegularFile(normalized))) {
            roots.add(normalized);
        }
    }

    private static String projectSortKey(MavenProject project) {
        if (project.getBasedir() != null) {
            return project.getBasedir().toPath().toAbsolutePath().normalize().toString();
        }
        return String.join(":", nullToEmpty(project.getGroupId()), nullToEmpty(project.getArtifactId()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
