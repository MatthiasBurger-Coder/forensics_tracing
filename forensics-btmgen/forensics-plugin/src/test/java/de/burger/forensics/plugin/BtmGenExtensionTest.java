package de.burger.forensics.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class BtmGenExtensionTest {

    @Test
    void providesConventionsForAllProperties() {
        var project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("de.burger.forensics.btmgen");

        BtmGenExtension extension = project.getExtensions().getByType(BtmGenExtension.class);

        assertThat(extension.getSrcDirs().get()).containsExactly("src/main/java");
        assertThat(extension.getPackagePrefixes().get()).isEmpty();
        assertThat(extension.getHelperFqn().get()).isEqualTo("org.example.trace.SafeEval");
        assertThat(extension.getSafeMode().get()).isTrue();
        assertThat(extension.getIncludeEntryExit().get()).isTrue();
        assertThat(extension.getIncludeTimestamp().get()).isTrue();
        assertThat(extension.getMinBranchesPerMethod().get()).isZero();
        assertThat(extension.getOutputDirectory().get().getAsFile().getName()).isEqualTo("forensics");
    }
}
