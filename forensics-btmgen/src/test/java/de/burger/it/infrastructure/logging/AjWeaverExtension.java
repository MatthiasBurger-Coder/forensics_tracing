package de.burger.it.infrastructure.logging;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Global JUnit 5 extension to ensure AspectJ LTW is installed as early as possible in tests.
 */
public class AjWeaverExtension implements BeforeAllCallback, BeforeEachCallback {
    static {
        // Attempt very early install during class load
        AjWeaverBootstrap.ensureInstalled();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        AjWeaverBootstrap.ensureInstalled();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        AjWeaverBootstrap.ensureInstalled();
    }
}
