package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.gradle.internal.PluginRuntimeLocator;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BtmGenPluginTest {

    @Test
    void appliesRuntimeHelperToJavaConfigurations() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("de.burger.forensics.btmgen");

        Optional<File> runtimeArtifact = PluginRuntimeLocator.locateFor(BtmGenPlugin.class);
        assertTrue(runtimeArtifact.isPresent(), "Expected plugin runtime artifact to be locatable");

        assertTrue(project.getConfigurations().getByName("runtimeClasspath").resolve().contains(runtimeArtifact.get()),
                "runtimeClasspath should include the plugin runtime helper");
        assertTrue(project.getConfigurations().getByName("testRuntimeClasspath").resolve().contains(runtimeArtifact.get()),
                "testRuntimeClasspath should include the plugin runtime helper");
    }
}
