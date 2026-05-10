package de.burger.forensics.adaptersupport.joern;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Process-based Joern command executor.
 */
public final class ProcessJoernCommandExecutor implements JoernCommandExecutor {

    @Override
    public JoernCommandResult execute(List<String> command, Duration timeout, Path workingDirectory) {
        Objects.requireNonNull(command, "Command must not be null.");
        Objects.requireNonNull(timeout, "Timeout must not be null.");
        Objects.requireNonNull(workingDirectory, "Working directory must not be null.");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty.");
        }
        ProcessBuilder builder = new ProcessBuilder(resolveCommand(command, System.getenv()));
        builder.directory(workingDirectory.toFile());
        try {
            Process process = builder.start();
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor();
                return new JoernCommandResult(-1, "", "Command timed out.");
            }
            return new JoernCommandResult(
                    process.exitValue(),
                    read(process.getInputStream()),
                    read(process.getErrorStream()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to execute Joern command " + command + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Joern command was interrupted.", e);
        }
    }

    private static String read(java.io.InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    static List<String> resolveCommand(List<String> command, Map<String, String> environment) {
        Objects.requireNonNull(command, "Command must not be null.");
        Objects.requireNonNull(environment, "Environment must not be null.");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty.");
        }
        List<String> resolved = new ArrayList<>(command);
        resolved.set(0, resolveExecutable(command.get(0), environment));
        return List.copyOf(resolved);
    }

    private static String resolveExecutable(String executable, Map<String, String> environment) {
        if (hasPathSeparator(executable) || Path.of(executable).isAbsolute()) {
            return executable;
        }
        return findExecutable(executable, environment)
                .map(Path::toString)
                .orElse(executable);
    }

    private static Optional<Path> findExecutable(String executable, Map<String, String> environment) {
        String pathValue = environmentValue(environment, "PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return Optional.empty();
        }
        for (String directory : pathValue.split(Pattern.quote(File.pathSeparator))) {
            Optional<Path> found = findExecutableInDirectory(executable, directory, environment);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> findExecutableInDirectory(
            String executable,
            String directory,
            Map<String, String> environment
    ) {
        if (directory == null || directory.isBlank()) {
            return Optional.empty();
        }
        Path directoryPath = Path.of(unquote(directory));
        for (String candidateName : candidateNames(executable, environment)) {
            Path candidate = directoryPath.resolve(candidateName);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private static List<String> candidateNames(String executable, Map<String, String> environment) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(executable);
        if (windows() && !hasFileExtension(executable)) {
            for (String extension : pathExtensions(environment)) {
                names.add(executable + extension);
            }
        }
        return List.copyOf(names);
    }

    private static List<String> pathExtensions(Map<String, String> environment) {
        String pathExt = environmentValue(environment, "PATHEXT");
        if (pathExt == null || pathExt.isBlank()) {
            pathExt = ".COM;.EXE;.BAT;.CMD";
        }
        return List.of(pathExt.split(Pattern.quote(File.pathSeparator))).stream()
                .filter(extension -> !extension.isBlank())
                .map(ProcessJoernCommandExecutor::normalizeExtension)
                .toList();
    }

    private static String normalizeExtension(String extension) {
        String trimmed = extension.trim();
        return trimmed.startsWith(".") ? trimmed : "." + trimmed;
    }

    private static String environmentValue(Map<String, String> environment, String key) {
        String exact = environment.get(key);
        if (exact != null) {
            return exact;
        }
        return environment.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static boolean hasPathSeparator(String value) {
        return value.contains("/") || value.contains("\\");
    }

    private static boolean hasFileExtension(String executable) {
        String fileName = Path.of(executable).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        return trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
    }

    private static boolean windows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
