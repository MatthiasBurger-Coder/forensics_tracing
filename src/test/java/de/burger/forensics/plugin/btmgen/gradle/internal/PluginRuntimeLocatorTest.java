package de.burger.forensics.plugin.btmgen.gradle.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;

import static org.assertj.core.api.Assertions.assertThat;

class PluginRuntimeLocatorTest {

    @Test
    void returnsEmptyWhenAnchorIsNull() {
        assertThat(PluginRuntimeLocator.locateFor(null)).isEmpty();
    }

    @Test
    void returnsEmptyWhenAnchorHasNoCodeSource() {
        assertThat(PluginRuntimeLocator.locateFor(String.class)).isEmpty();
    }

    @Test
    void returnsExistingLocationForProjectClasses() {
        assertThat(PluginRuntimeLocator.locateFor(PluginRuntimeLocatorTest.class))
                .isPresent()
                .get()
                .satisfies(file -> assertThat(file).exists());
    }

    @Test
    void returnsEmptyWhenAnchorLocationDoesNotExist(@TempDir Path tempDir) throws IOException {
        URL missingLocation = tempDir.resolve("missing-runtime.jar").toUri().toURL();
        Class<?> anchor = loadAnchorWithLocation(missingLocation);

        assertThat(PluginRuntimeLocator.locateFor(anchor)).isEmpty();
    }

    private static Class<?> loadAnchorWithLocation(URL location) throws IOException {
        byte[] bytes;
        try (var stream = PluginRuntimeLocatorTest.class.getResourceAsStream("PluginRuntimeLocatorTest$AnchorTemplate.class")) {
            assertThat(stream).isNotNull();
            bytes = stream.readAllBytes();
        }

        ProtectionDomain protectionDomain = new ProtectionDomain(
                new CodeSource(location, (Certificate[]) null),
                null
        );

        class AnchorLoader extends SecureClassLoader {
            private Class<?> define(byte[] classBytes, ProtectionDomain domain) {
                return defineClass(AnchorTemplate.class.getName(), classBytes, 0, classBytes.length, domain);
            }
        }

        return new AnchorLoader().define(bytes, protectionDomain);
    }

    private static final class AnchorTemplate {
    }
}
