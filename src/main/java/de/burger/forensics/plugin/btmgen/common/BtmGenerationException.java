package de.burger.forensics.plugin.btmgen.common;

/**
 * Common generation failure that build-tool adapters can translate to their own exception type.
 */
public final class BtmGenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BtmGenerationException(String message) {
        super(message);
    }

    public BtmGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
