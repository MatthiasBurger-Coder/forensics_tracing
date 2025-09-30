package de.burger.forensics.plugin;

import de.burger.forensics.application.service.GenerateRulesUseCase;
import de.burger.forensics.application.service.GenerationRequest;
import de.burger.forensics.application.service.RuleGenerationResult;
import de.burger.forensics.plugin.io.BtmFileWriter;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Task delegating to the {@link GenerateRulesUseCase}.
 */
@CacheableTask
public abstract class GenerateBtmTask extends DefaultTask {

    private GenerateRulesUseCase useCase;
    private BtmFileWriter fileWriter = new BtmFileWriter(Clock.systemDefaultZone());

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceDirectories();

    @Input
    public abstract Property<String> getHelperFqn();

    @Input
    public abstract Property<Boolean> getSafeMode();

    @Input
    public abstract Property<Boolean> getIncludeEntryExit();

    @Input
    public abstract Property<Boolean> getIncludeTimestamp();

    @Input
    public abstract Property<Integer> getMinBranchesPerMethod();

    @Input
    public abstract ListProperty<String> getPackagePrefixes();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    void setUseCase(GenerateRulesUseCase useCase) {
        this.useCase = useCase;
    }

    void setFileWriter(BtmFileWriter fileWriter) {
        this.fileWriter = Objects.requireNonNull(fileWriter, "fileWriter");
    }

    @TaskAction
    public void runGenerator() {
        Objects.requireNonNull(useCase, "useCase must be configured by the plugin");

        List<Path> roots = getSourcePaths();
        List<String> prefixes = getPackagePrefixes().getOrElse(List.of());
        Set<String> aggregatedRules = new LinkedHashSet<>();

        for (Path root : roots) {
            GenerationRequest request = buildRequest(root, prefixes);
            RuleGenerationResult result = useCase.generate(request);
            aggregatedRules.addAll(result.renderedRules());
        }

        Path output = getOutputFile().get().getAsFile().toPath();
        fileWriter.write(
            output,
            getIncludeTimestamp().getOrElse(true),
            getHelperFqn().get(),
            List.copyOf(aggregatedRules)
        );
    }

    private List<Path> getSourcePaths() {
        return getSourceDirectories().getFiles().stream()
            .map(File::toPath)
            .toList();
    }

    private GenerationRequest buildRequest(Path root, List<String> prefixes) {
        return new GenerationRequest(
            root,
            getHelperFqn().get(),
            getSafeMode().getOrElse(true),
            getIncludeEntryExit().getOrElse(true),
            prefixes,
            getMinBranchesPerMethod().getOrElse(0),
            List.of()
        );
    }
}
