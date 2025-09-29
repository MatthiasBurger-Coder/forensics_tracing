package de.burger.forensics.plugin;

import de.burger.forensics.application.service.GenerateRulesUseCase;
import de.burger.forensics.application.service.GenerationRequest;
import de.burger.forensics.application.service.RuleGenerationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    @TaskAction
    public void runGenerator() {
        Objects.requireNonNull(useCase, "useCase must be configured by the plugin");

        List<String> prefixes = getPackagePrefixes().getOrElse(List.of());
        Set<String> allRules = new LinkedHashSet<>();

        for (Path root : getSourcePaths()) {
            GenerationRequest request = new GenerationRequest(
                root,
                getHelperFqn().get(),
                getSafeMode().getOrElse(true),
                getIncludeEntryExit().getOrElse(true),
                prefixes,
                getMinBranchesPerMethod().getOrElse(0),
                List.of()
            );
            RuleGenerationResult result = useCase.generate(request);
            allRules.addAll(result.renderedRules());
        }

        writeOutput(new ArrayList<>(allRules));
    }

    private List<Path> getSourcePaths() {
        List<Path> paths = new ArrayList<>();
        getSourceDirectories().getFiles().forEach(file -> paths.add(file.toPath()));
        return paths;
    }

    private void writeOutput(List<String> rules) {
        Path output = getOutputFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(output.getParent());
            List<String> lines = new ArrayList<>();
            if (getIncludeTimestamp().getOrElse(true)) {
                lines.add("# Generated at " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()));
            } else {
                lines.add("# Generated rules");
            }
            lines.add("# Helper: " + getHelperFqn().get());
            if (!rules.isEmpty()) {
                lines.add("# Rule count: " + rules.size());
            }
            lines.add("");
            Files.write(output, lines, StandardCharsets.UTF_8);
            try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND)) {
                for (String rule : rules) {
                    writer.write(rule);
                    writer.write(System.lineSeparator());
                    writer.write(System.lineSeparator());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write generated rules", e);
        }
    }
}
