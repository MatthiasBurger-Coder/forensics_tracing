package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class MavenMojoTestSupport {

    private MavenMojoTestSupport() {
    }

    static MavenProject project(Path projectDirectory, String artifactId) throws Exception {
        Files.createDirectories(projectDirectory);
        Model model = new Model();
        model.setGroupId("de.burger.forensics");
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        MavenProject project = new MavenProject(model);
        project.setFile(projectDirectory.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(projectDirectory.resolve("target").toString());
        project.setBuild(build);
        return project;
    }

    static MavenProject projectWithBuildDirectory(Path projectDirectory) {
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

    static MavenSession session(List<MavenProject> projects) {
        MavenSession session = mock(MavenSession.class);
        when(session.getProjects()).thenReturn(projects);
        return session;
    }

    static Path createSampleSource(Path sourceRoot) throws Exception {
        return createSource(sourceRoot, "com.example", "Sample");
    }

    static Path createSource(Path sourceRoot, String packageName, String className) throws Exception {
        Path packageDirectory = sourceRoot.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve(className + ".java"), """
                package %s;
                public class %s {
                  public int run(int value) {
                    if (value > 0) { }
                    return value;
                  }
                }
                """.formatted(packageName, className));
        return sourceRoot;
    }

    static Path sourceRoot(MavenProject project, String relativePath) throws Exception {
        Path root = Files.createDirectories(project.getBasedir().toPath().resolve(relativePath));
        project.addCompileSourceRoot(root.toString());
        return root.toAbsolutePath().normalize();
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static final class SilentLog implements Log {
        @Override public boolean isDebugEnabled() { return true; }
        @Override public void debug(CharSequence content) { }
        @Override public void debug(CharSequence content, Throwable error) { }
        @Override public void debug(Throwable error) { }
        @Override public boolean isInfoEnabled() { return true; }
        @Override public void info(CharSequence content) { }
        @Override public void info(CharSequence content, Throwable error) { }
        @Override public void info(Throwable error) { }
        @Override public boolean isWarnEnabled() { return true; }
        @Override public void warn(CharSequence content) { }
        @Override public void warn(CharSequence content, Throwable error) { }
        @Override public void warn(Throwable error) { }
        @Override public boolean isErrorEnabled() { return true; }
        @Override public void error(CharSequence content) { }
        @Override public void error(CharSequence content, Throwable error) { }
        @Override public void error(Throwable error) { }
    }
}
