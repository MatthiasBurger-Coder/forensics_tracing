package de.burger.forensics.plugin.engine;

import java.util.List;

/**
 * Scans a Java source text and returns Byteman rules for methods/branches found.
 * The emitter uses METHOD(name(..)) and escapes/truncates strings as needed.
 */
public interface JavaScanner {
    List<String> scan(
            String text,
            String helperFqn,
            String packagePrefix,
            boolean includeEntryExit,
            int maxStringLength);
}
