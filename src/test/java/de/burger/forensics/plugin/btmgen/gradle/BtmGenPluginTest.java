package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.gradle.internal.PluginRuntimeLocator;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BtmGenPluginTest {
    private static final String RUNTIME_HELPER_ATTACHED_MARKER = "de.burger.forensics.btmgen.runtimeHelperAttached";

    @Test
    void registersActivityPumlTask() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("de.burger.forensics.btmgen");

        var task = project.getTasks().findByName("generateActivityPumlFromBtm");
        assertNotNull(task, "generateActivityPumlFromBtm task should be registered");
        assertEquals("forensics", task.getGroup());
    }

    @Test
    void registersTraceActivityPumlTask() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("de.burger.forensics.btmgen");

        var task = project.getTasks().findByName("generateActivityPumlFromTrace");
        assertNotNull(task, "generateActivityPumlFromTrace task should be registered");
        assertEquals("forensics", task.getGroup());
    }

    @Test
    void appliesRuntimeHelperToJavaConfigurations() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("de.burger.forensics.btmgen");

        File runtimeArtifact = runtimeArtifact();

        assertTrue(project.getConfigurations().getByName("runtimeClasspath").resolve().contains(runtimeArtifact),
                "runtimeClasspath should include the plugin runtime helper");
        assertTrue(project.getConfigurations().getByName("testRuntimeClasspath").resolve().contains(runtimeArtifact),
                "testRuntimeClasspath should include the plugin runtime helper");
    }

    @Test
    void appliesRuntimeHelperToJavaSubprojectsWhenMonorepoScanningIsEnabled() {
        Project rootProject = ProjectBuilder.builder().build();
        Project subproject = ProjectBuilder.builder().withParent(rootProject).withName("module-a").build();

        rootProject.getPlugins().apply("de.burger.forensics.btmgen");
        rootProject.getExtensions().getByType(BtmGenExtension.class).getScanSubprojects().set(true);
        subproject.getPlugins().apply("java-library");

        File runtimeArtifact = runtimeArtifact();

        assertTrue(subproject.getConfigurations().getByName("runtimeClasspath").resolve().contains(runtimeArtifact),
                "runtimeClasspath should include the plugin runtime helper in Java subprojects");
        assertTrue(subproject.getConfigurations().getByName("testRuntimeClasspath").resolve().contains(runtimeArtifact),
                "testRuntimeClasspath should include the plugin runtime helper in Java subprojects");
        assertEquals(1L, fileDependencyCount(subproject, "runtimeOnly"));
        assertEquals(1L, fileDependencyCount(subproject, "testRuntimeOnly"));
    }

    @Test
    void ignoresNonJavaSubprojectsWhenMonorepoScanningIsEnabled() {
        Project rootProject = ProjectBuilder.builder().build();
        Project subproject = ProjectBuilder.builder().withParent(rootProject).withName("docs").build();

        rootProject.getPlugins().apply("de.burger.forensics.btmgen");
        rootProject.getExtensions().getByType(BtmGenExtension.class).getScanSubprojects().set(true);

        assertNull(subproject.getConfigurations().findByName("runtimeOnly"));
        assertNull(subproject.getConfigurations().findByName("testRuntimeOnly"));
        assertFalse(subproject.getExtensions().getExtraProperties().has(RUNTIME_HELPER_ATTACHED_MARKER));
    }

    @Test
    void realizeGenerateBtmRulesUsesDefaultConventions() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("de.burger.forensics.btmgen");

        GenerateBtmTask task = (GenerateBtmTask) project.getTasks().getByName("generateBtmRules");

        assertEquals("forensics", task.getGroup());
        assertEquals("Generates Byteman (.btm) rules by scanning Java sources.", task.getDescription());
        assertTrue(task.getSourceRoot().get().getAsFile().toPath().endsWith("src\\main\\java")
            || task.getSourceRoot().get().getAsFile().toPath().endsWith("src/main/java"));
        assertTrue(task.getOutputFile().get().getAsFile().toPath().endsWith("forensics\\forensics.btm")
            || task.getOutputFile().get().getAsFile().toPath().endsWith("forensics/forensics.btm"));
    }

    @Test
    void realizeGenerateBtmRulesHonorsExplicitExtensionValues() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("de.burger.forensics.btmgen");
        BtmGenExtension extension = project.getExtensions().getByType(BtmGenExtension.class);
        File sourceRoot = project.file("custom-src");
        File outputFile = project.file("custom-out/generated.btm");
        extension.getSourceRoot().set(sourceRoot);
        extension.getOutputFile().set(outputFile);

        GenerateBtmTask task = (GenerateBtmTask) project.getTasks().getByName("generateBtmRules");
        var buildTask = project.getTasks().getByName("build");

        assertEquals(sourceRoot, task.getSourceRoot().get().getAsFile());
        assertEquals(outputFile, task.getOutputFile().get().getAsFile());
        assertEquals(outputFile.getParentFile(), task.getOutputDir().get().getAsFile());
        assertTrue(buildTask.getTaskDependencies().getDependencies(buildTask).contains(task));
    }

    @Test
    void doesNotAttachRuntimeHelperDependencyTwice() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("de.burger.forensics.btmgen");

        assertEquals(1L, fileDependencyCount(project, "runtimeOnly"));
        assertEquals(1L, fileDependencyCount(project, "testRuntimeOnly"));
    }

    @Test
    void addDependencyIfPresentSkipsMissingConfigurations() throws Exception {
        Project project = ProjectBuilder.builder().build();
        BtmGenPlugin plugin = new BtmGenPlugin();
        Method method = BtmGenPlugin.class.getDeclaredMethod("addDependencyIfPresent", Project.class, String.class, FileCollection.class);
        method.setAccessible(true);

        Object attached = method.invoke(plugin, project, "missingConfiguration", project.files("runtime.jar"));

        assertNull(project.getConfigurations().findByName("missingConfiguration"));
        assertEquals(false, attached);
    }

    private static File runtimeArtifact() {
        Optional<File> runtimeArtifact = PluginRuntimeLocator.locateFor(BtmGenPlugin.class);
        assertTrue(runtimeArtifact.isPresent(), "Expected plugin runtime artifact to be locatable");
        return runtimeArtifact.get();
    }

    private static long fileDependencyCount(Project project, String configurationName) {
        return project.getConfigurations().getByName(configurationName)
                .getDependencies()
                .stream()
                .filter(dependency -> dependency.getGroup() == null)
                .count();
    }
}
