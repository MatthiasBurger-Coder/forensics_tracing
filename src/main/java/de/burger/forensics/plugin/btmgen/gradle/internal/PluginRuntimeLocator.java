package de.burger.forensics.plugin.btmgen.gradle.internal;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility to locate the jar or classes directory that contains the plugin runtime helper.
 */
public final class PluginRuntimeLocator {

    private PluginRuntimeLocator() {
    }

    public static Optional<File> locateFor(Class<?> anchor) {
        if (anchor == null) {
            return Optional.empty();
        }

        URL location = anchor.getProtectionDomain().getCodeSource() != null
                ? anchor.getProtectionDomain().getCodeSource().getLocation()
                : null;
        if (location == null) {
            return Optional.empty();
        }

        try {
            Path path = Path.of(location.toURI());
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            return Optional.of(path.toFile());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
