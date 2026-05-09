package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Collects existing Java source roots from a Maven reactor in deterministic order.
 */
final class MavenReactorSourceRootCollector {

    List<Path> collect(MavenSession session, MavenProject rootProject, boolean includeTests) {
        Objects.requireNonNull(rootProject, "rootProject");
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        reactorProjects(session, rootProject).forEach(project -> addProjectRoots(roots, project, includeTests));
        return List.copyOf(roots);
    }

    private static List<MavenProject> reactorProjects(MavenSession session, MavenProject rootProject) {
        if (session == null || session.getProjects() == null || session.getProjects().isEmpty()) {
            return List.of(rootProject);
        }
        return session.getProjects().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MavenReactorSourceRootCollector::projectOrderKey))
                .toList();
    }

    private static String projectOrderKey(MavenProject project) {
        if (project.getBasedir() != null) {
            return project.getBasedir().toPath().toAbsolutePath().normalize().toString();
        }
        String groupId = project.getGroupId() == null ? "" : project.getGroupId();
        String artifactId = project.getArtifactId() == null ? "" : project.getArtifactId();
        return groupId + ":" + artifactId;
    }

    private static void addProjectRoots(LinkedHashSet<Path> roots, MavenProject project, boolean includeTests) {
        project.getCompileSourceRoots().forEach(root -> addExistingRoot(roots, Path.of(root)));
        if (includeTests) {
            project.getTestCompileSourceRoots().forEach(root -> addExistingRoot(roots, Path.of(root)));
        }
    }

    private static void addExistingRoot(LinkedHashSet<Path> roots, Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (Files.exists(normalized) && (Files.isDirectory(normalized) || Files.isRegularFile(normalized))) {
            roots.add(normalized);
        }
    }
}
