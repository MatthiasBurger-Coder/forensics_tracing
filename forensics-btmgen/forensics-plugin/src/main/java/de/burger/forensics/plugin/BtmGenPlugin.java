package de.burger.forensics.plugin;

import de.burger.forensics.adapters.byteman.BytemanRuleRenderer;
import de.burger.forensics.adapters.javaparser.JavaParserScanner;
import de.burger.forensics.application.service.GenerateRulesUseCase;
import de.burger.forensics.domain.port.out.CodeScanPort;
import de.burger.forensics.domain.port.out.RuleRenderPort;
import de.burger.forensics.domain.strategy.DefaultStrategyFactory;
import de.burger.forensics.domain.strategy.StrategyFactory;
import de.burger.forensics.plugin.adapters.GradleLogAdapter;
import de.burger.forensics.plugin.adapters.SystemClockAdapter;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/**
 * Registers the {@link GenerateBtmTask} and wires the default ports.
 */
public final class BtmGenPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        BtmGenExtension extension = project.getExtensions().create(
            "forensicsBtmGen",
            BtmGenExtension.class,
            project.getObjects(),
            project.getLayout()
        );

        CodeScanPort scanner = new JavaParserScanner();
        RuleRenderPort renderer = new BytemanRuleRenderer();
        StrategyFactory strategyFactory = new DefaultStrategyFactory();

        TaskProvider<GenerateBtmTask> task = project.getTasks().register(
            "generateBtmRules",
            GenerateBtmTask.class,
            t -> {
                t.getHelperFqn().set(extension.getHelperFqn());
                t.getSafeMode().set(extension.getSafeMode());
                t.getIncludeEntryExit().set(extension.getIncludeEntryExit());
                t.getIncludeTimestamp().set(extension.getIncludeTimestamp());
                t.getMinBranchesPerMethod().set(extension.getMinBranchesPerMethod());
                t.getPackagePrefixes().set(extension.getPackagePrefixes());
                t.getOutputFile().set(extension.getOutputDirectory().file("rules.btm"));
                t.getSourceDirectories().from(extension.getSrcDirs().map(dirs ->
                    dirs.stream().map(project::file).toList()
                ));

                GenerateRulesUseCase useCase = new GenerateRulesUseCase(
                    scanner,
                    renderer,
                    new SystemClockAdapter(),
                    new GradleLogAdapter(project.getLogger()),
                    strategyFactory
                );
                t.setUseCase(useCase);
            }
        );

        project.getTasks().named("build").configure(build -> build.dependsOn(task));
    }
}
