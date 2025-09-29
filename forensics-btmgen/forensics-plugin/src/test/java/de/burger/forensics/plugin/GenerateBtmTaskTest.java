package de.burger.forensics.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateBtmTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesRulesFile() throws IOException {
        Path sources = tempDir.resolve("src");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("Demo.java"), """
            package demo;
            public class Demo {
              public void sample(int value) {
                if (value > 0) {
                  System.out.println(value);
                }
              }
            }
            """
        );

        var project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        project.getPluginManager().apply("de.burger.forensics.btmgen");

        BtmGenExtension extension = project.getExtensions().getByType(BtmGenExtension.class);
        extension.getSrcDirs().set(List.of(sources.toString()));
        extension.getIncludeTimestamp().set(false);
        extension.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("out"));

        GenerateBtmTask task = (GenerateBtmTask) project.getTasks().getByName("generateBtmRules");
        task.runGenerator();

        Path output = extension.getOutputDirectory().get().file("rules.btm").getAsFile().toPath();
        assertThat(Files.exists(output)).isTrue();
        String content = Files.readString(output);
        assertThat(content).contains("RULE").contains("demo.Demo");
    }
}
