package de.burger.forensics.adaptersupport.joern;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
        ProcessBuilder builder = new ProcessBuilder(command);
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
}
