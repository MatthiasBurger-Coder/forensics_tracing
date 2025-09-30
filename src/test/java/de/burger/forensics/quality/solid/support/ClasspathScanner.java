package de.burger.forensics.quality.solid.support;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Lightweight classpath scanner that discovers compiled classes without external libraries.
 */
public final class ClasspathScanner {

    private ClasspathScanner() {
    }

    public static Set<Class<?>> scanPackage(String basePackage) {
        String path = basePackage.replace('.', '/');
        Set<Class<?>> classes = new LinkedHashSet<>();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if (isTestClasses(url)) {
                    continue;
                }
                switch (url.getProtocol()) {
                    case "file" -> classes.addAll(scanDirectory(url, basePackage));
                    case "jar" -> classes.addAll(scanJar(url, path));
                    default -> {
                        // ignore other protocols
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan package " + basePackage, e);
        }
        return classes;
    }

    private static boolean isTestClasses(URL url) {
        String file = url.getFile();
        return file.contains("classes/java/test");
    }

    private static Set<Class<?>> scanDirectory(URL url, String basePackage) {
        try {
            Path directory = Paths.get(url.toURI());
            Set<Class<?>> classes = new LinkedHashSet<>();
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith(".class")) {
                        String className = toClassName(directory, file, basePackage);
                        if (!className.contains("$")) {
                            classes.add(loadClass(className));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return classes;
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to read directory for package " + basePackage, e);
        }
    }

    private static Set<Class<?>> scanJar(URL url, String path) {
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                Set<Class<?>> classes = new LinkedHashSet<>();
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(path) || !name.endsWith(".class") || name.contains("$")) {
                        continue;
                    }
                    if (name.contains("classes/java/test/")) {
                        continue;
                    }
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    classes.add(loadClass(className));
                }
                return classes;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read jar for path " + path, e);
        }
    }

    private static String toClassName(Path root, Path file, String basePackage) {
        Path relative = root.relativize(file);
        String className = relative.toString()
            .replace('\\', '.')
            .replace('/', '.');
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }
        return basePackage + (className.isEmpty() ? "" : "." + className);
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load class " + name, e);
        }
    }
}
