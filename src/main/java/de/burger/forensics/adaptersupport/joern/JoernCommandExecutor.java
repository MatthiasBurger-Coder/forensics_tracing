package de.burger.forensics.adaptersupport.joern;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Executes one external Joern command.
 */
public interface JoernCommandExecutor {

    JoernCommandResult execute(List<String> command, Duration timeout, Path workingDirectory);
}
