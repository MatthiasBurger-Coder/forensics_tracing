package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BtmGenExtensionTest {

    @Test
    void setRegistryNullRestoresDefaultRegistry() {
        var project = ProjectBuilder.builder().build();
        var extension = project.getObjects().newInstance(BtmGenExtension.class);

        extension.setRegistry(null);

        assertEquals(StrategyRegistries.defaultRegistry().ids(), extension.getRegistry().ids());
    }
}
