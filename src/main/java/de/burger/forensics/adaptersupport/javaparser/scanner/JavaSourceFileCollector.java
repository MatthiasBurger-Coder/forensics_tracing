package de.burger.forensics.adaptersupport.javaparser.scanner;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Collects Java source files while skipping symbolic-link directories.
 */
public final class JavaSourceFileCollector {

    private static final int MAX_SCAN_DEPTH = 64;
    private static final Set<String> TOOL_DIRECTORY_NAMES = Set.of(".git", ".gradle", ".idea");
    private static final Set<String> BUILD_OUTPUT_DIRECTORY_NAMES = Set.of("build", "target", "out");
    private static final List<List<String>> JAVA_SOURCE_ROOT_SEGMENTS = List.of(
            List.of("src", "main", "java"),
            List.of("src", "test", "java"),
            List.of("src", "integrationTest", "java")
    );
    private static final List<List<String>> DEFAULT_EXCLUDED_SOURCE_ROOT_SEGMENTS = List.of(
            List.of("src", "test", "java"),
            List.of("src", "integrationTest", "java")
    );

    public List<Path> collect(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<Path> sourceFiles = new ArrayList<>();
        try {
            Files.walkFileTree(
                    normalizedRoot,
                    EnumSet.noneOf(FileVisitOption.class),
                    MAX_SCAN_DEPTH,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            return shouldSkipDirectory(normalizedRoot, dir.toAbsolutePath().normalize())
                                    ? FileVisitResult.SKIP_SUBTREE
                                    : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (attrs.isRegularFile() && isJavaSource(file)) {
                                sourceFiles.add(file);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exception) {
                            return FileVisitResult.CONTINUE;
                        }
                    }
            );
            return sourceFiles.stream().sorted().toList();
        } catch (IOException ignored) {
            // Ignore traversal failures to avoid failing the build on single-file issues.
            return List.of();
        }
    }

    private static boolean shouldSkipDirectory(Path root, Path directory) {
        String directoryName = fileName(directory).toLowerCase(Locale.ROOT);
        if (TOOL_DIRECTORY_NAMES.contains(directoryName)) {
            return true;
        }
        if (isDefaultExcludedSourceRoot(root, directory)) {
            return true;
        }
        return BUILD_OUTPUT_DIRECTORY_NAMES.contains(directoryName)
                && isBuildOutputDirectory(root, directory);
    }

    private static boolean isDefaultExcludedSourceRoot(Path root, Path directory) {
        if (directory.equals(root)) {
            return false;
        }
        return DEFAULT_EXCLUDED_SOURCE_ROOT_SEGMENTS.stream()
                .anyMatch(segments -> endsWithSegments(directory, segments));
    }

    private static boolean isBuildOutputDirectory(Path root, Path directory) {
        return !isJavaSourceRoot(root) && !isBelowMainSourceRoot(root, directory);
    }

    private static boolean isJavaSourceRoot(Path path) {
        return JAVA_SOURCE_ROOT_SEGMENTS.stream()
                .anyMatch(segments -> endsWithSegments(path, segments));
    }

    private static boolean isBelowMainSourceRoot(Path root, Path directory) {
        return containsSegments(root.relativize(directory), List.of("src", "main", "java"));
    }

    private static boolean isJavaSource(Path file) {
        return file.getFileName().toString().endsWith(".java");
    }

    private static boolean endsWithSegments(Path path, List<String> suffix) {
        List<String> names = pathNames(path);
        if (names.size() < suffix.size()) {
            return false;
        }
        return matchesAt(names, suffix, names.size() - suffix.size());
    }

    private static boolean containsSegments(Path path, List<String> segments) {
        List<String> names = pathNames(path);
        if (names.size() < segments.size()) {
            return false;
        }
        for (int start = 0; start <= names.size() - segments.size(); start++) {
            if (matchesAt(names, segments, start)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAt(List<String> names, List<String> expected, int start) {
        for (int index = 0; index < expected.size(); index++) {
            if (!names.get(start + index).equalsIgnoreCase(expected.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> pathNames(Path path) {
        List<String> names = new ArrayList<>(path.getNameCount());
        for (Path part : path) {
            names.add(part.toString());
        }
        return names;
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }
}
