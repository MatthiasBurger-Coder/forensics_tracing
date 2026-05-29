package de.burger.forensics.plugin.btmgen.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BtmGenPluginTest {

    @Test
    void registersOnlyGrpcSubmissionTasks() {
        Project project = ProjectBuilder.builder().withName("demo").build();

        project.getPlugins().apply("de.burger.forensics.btmgen");

        assertThat(project.getExtensions().findByName("forensicsTracing")).isInstanceOf(BtmGenExtension.class);
        assertThat(project.getTasks().findByName("submitForensicsAnalysis")).isInstanceOf(SubmitForensicsAnalysisTask.class);
        assertThat(project.getTasks().findByName("forensicsAnalyze")).isNotNull();
        assertThat(project.getTasks().findByName("generateBtmRules")).isNull();
        assertThat(project.getTasks().findByName("analyzeForensicsSemantics")).isNull();
        assertThat(project.getTasks().findByName("importForensicsSemantics")).isNull();
        assertThat(project.getTasks().findByName("cleanForensicsAnalysisStore")).isNull();
    }

    @Test
    void mapsExtensionValuesIntoSubmissionTask() {
        Project project = ProjectBuilder.builder().withName("demo").build();
        project.setVersion("1.2.3");
        project.getPlugins().apply("de.burger.forensics.btmgen");
        BtmGenExtension extension = project.getExtensions().getByType(BtmGenExtension.class);
        extension.getServerHost().set("analytics.example.test");
        extension.getServerPort().set(9443);
        extension.getPlaintext().set(false);
        extension.getProjectId().set("project-a");
        extension.getRepositoryUrl().set("https://example.test/repo.git");
        extension.getCommitHash().set("abc123");
        extension.getBuildId().set("build-42");
        extension.getModuleName().set("module-a");
        extension.getModulePath().set(":module-a");

        SubmitForensicsAnalysisTask task = (SubmitForensicsAnalysisTask) project.getTasks()
                .getByName("submitForensicsAnalysis");

        assertThat(task.getServerHost().get()).isEqualTo("analytics.example.test");
        assertThat(task.getServerPort().get()).isEqualTo(9443);
        assertThat(task.getPlaintext().get()).isFalse();
        assertThat(task.getPluginVersion().get()).isEqualTo("1.2.3");
        assertThat(task.toSubmission().projectId()).isEqualTo("project-a");
        assertThat(task.toSubmission().repositoryUrl()).isEqualTo("https://example.test/repo.git");
        assertThat(task.toSubmission().commitHash()).isEqualTo("abc123");
        assertThat(task.toSubmission().buildId()).isEqualTo("build-42");
        assertThat(task.toSubmission().moduleName()).isEqualTo("module-a");
        assertThat(task.toSubmission().modulePath()).isEqualTo(":module-a");
    }
}
