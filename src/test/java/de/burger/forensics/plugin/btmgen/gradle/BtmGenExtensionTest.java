package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtmGenExtensionTest {

    @Test
    void setRegistryNullRestoresDefaultRegistry() {
        var project = ProjectBuilder.builder().build();
        var extension = project.getObjects().newInstance(BtmGenExtension.class);

        extension.setRegistry(null);

        assertEquals(StrategyRegistries.defaultRegistry().ids(), extension.getRegistry().ids());
    }

    @Test
    void analysisStoreDefaultsAreConfigured() {
        var project = ProjectBuilder.builder().build();
        var extension = project.getObjects().newInstance(BtmGenExtension.class);

        assertTrue(extension.getAnalysisStoreEnabled().get());
        assertTrue(!extension.getIncludeTests().get());
        assertEquals("", extension.getExcludes().get());
        assertEquals(new File("build/forensics/analysis-store"), extension.getAnalysisStoreDirectory().get());
        assertEquals("KEEP_ON_SUCCESS", extension.getCleanupPolicy().get());
        assertEquals(new File("build/forensics/manifest.json"), extension.getManifestFile().get());
        assertEquals(new File("build/forensics/checksums.sha256"), extension.getChecksumsFile().get());
        assertTrue(!extension.getEngineRequestEnabled().get());
        assertEquals(new File("build/forensics/engine-request.json"), extension.getEngineRequestFile().get());
    }

    @Test
    void joernDefaultsAreConfiguredAsDisabled() {
        var project = ProjectBuilder.builder().build();
        var extension = project.getObjects().newInstance(BtmGenExtension.class);

        assertTrue(!extension.getJoernEnabled().get());
        assertEquals(new File("joern"), extension.getJoernExecutable().get());
        assertEquals(new File("joern-parse"), extension.getJoernParseExecutable().get());
        assertEquals(new File("joern-slice"), extension.getJoernSliceExecutable().get());
        assertEquals(new File("build/forensics/joern/workspace"), extension.getJoernWorkspaceDirectory().get());
        assertEquals(new File("build/forensics/joern"), extension.getJoernOutputDirectory().get());
        assertEquals("", extension.getJoernMaxHeap().get());
        assertEquals(300, extension.getJoernTimeoutSeconds().get());
        assertTrue(extension.getJoernFailOnError().get());
    }
}
