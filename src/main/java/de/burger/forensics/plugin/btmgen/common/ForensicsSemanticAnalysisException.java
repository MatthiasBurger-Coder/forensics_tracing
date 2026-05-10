package de.burger.forensics.plugin.btmgen.common;

/**
 * Signals a build-tool-neutral semantic analysis failure.
 */
public final class ForensicsSemanticAnalysisException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForensicsSemanticAnalysisException(String message) {
        super(message);
    }

    public ForensicsSemanticAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
