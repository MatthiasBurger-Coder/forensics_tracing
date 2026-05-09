package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.project.MavenProject;

import java.nio.file.Path;

/**
 * Resolves Maven build directories consistently for Maven connector adapters.
 */
final class MavenBuildDirectories {

    private MavenBuildDirectories() {
    }

    static Path buildDirectory(MavenProject project) {
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
}
