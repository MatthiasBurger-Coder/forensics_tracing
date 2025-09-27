package de.burger.it.infrastructure.logging;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public class AjWeaverSessionListener implements LauncherSessionListener {
    @Override
    public void launcherSessionOpened(LauncherSession session) {
        AjWeaverBootstrap.ensureInstalled();
    }
}
