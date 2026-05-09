package de.burger.forensics.adaptersupport.joern;

/**
 * Result of one external Joern command.
 */
public record JoernCommandResult(int exitCode, String stdout, String stderr) {

    public JoernCommandResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }

    public boolean successful() {
        return exitCode == 0;
    }
}
