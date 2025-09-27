package de.burger.forensics.plugin.engine;

import java.io.File;
import java.util.function.Consumer;

public final class SourceFileGuards {
    private SourceFileGuards() {}

    public static boolean shouldSkipLargeFile(File file, long maxBytes, Consumer<String> debug) {
        if (maxBytes <= 0) return false;
        long length = file.length();
        if (length > maxBytes) {
            if (debug != null) {
                debug.accept("Skipping large file (" + length + " bytes > limit " + maxBytes + "): " + file.getAbsolutePath());
            }
            return true;
        }
        return false;
    }
}
