package de.burger.forensics.adaptersupport.joern;

/**
 * Signals a failed Joern semantic analysis command or artifact import.
 */
public final class JoernAnalysisException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JoernAnalysisException(String message) {
        super(message);
    }

    public JoernAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
