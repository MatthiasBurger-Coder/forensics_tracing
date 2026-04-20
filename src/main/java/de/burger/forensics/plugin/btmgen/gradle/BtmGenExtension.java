package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;

/**
 * Extension to configure sourceRoot, outputFile and provide a StrategyRegistry.
 */
public class BtmGenExtension {
    private StrategyRegistry registry = StrategyRegistries.defaultRegistry();
    private final Property<@NotNull File> sourceRoot;
    private final ConfigurableFileCollection sourceRoots;
    private final Property<@NotNull File> outputFile;
    private final Property<@NotNull String> includes;
    private final Property<@NotNull String> helperFqn;
    private final Property<@NotNull Integer> minBranchesPerMethod;
    private final Property<@NotNull Boolean> scanSubprojects;

    @Inject
    public BtmGenExtension(ObjectFactory objects) {
        this.sourceRoot = objects.property(File.class);
        this.sourceRoots = objects.fileCollection();
        this.outputFile = objects.property(File.class);
        this.includes = objects.property(String.class);
        this.helperFqn = objects.property(String.class);
        this.minBranchesPerMethod = objects.property(Integer.class);
        this.scanSubprojects = objects.property(Boolean.class);
        this.sourceRoot.convention(new File("src/main/java"));
        this.sourceRoots.from(new File("src/main/java"));
        this.outputFile.convention(new File("build/forensics/forensics.btm"));
        this.helperFqn.convention(RuleParams.DEFAULT_HELPER_FQN);
        this.minBranchesPerMethod.convention(2);
        this.scanSubprojects.convention(false);
    }

    public StrategyRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(StrategyRegistry registry) {
        this.registry = registry == null ? StrategyRegistries.defaultRegistry() : registry;
    }

    public Property<@NotNull File> getSourceRoot() {
        return sourceRoot;
    }

    public ConfigurableFileCollection getSourceRoots() {
        return sourceRoots;
    }

    public Property<@NotNull File> getOutputFile() {
        return outputFile;
    }

    public Property<@NotNull String> getIncludes() {
        return includes;
    }

    public Property<@NotNull String> getHelperFqn() {
        return helperFqn;
    }

    public Property<@NotNull Integer> getMinBranchesPerMethod() {
        return minBranchesPerMethod;
    }

    public Property<@NotNull Boolean> getScanSubprojects() {
        return scanSubprojects;
    }
}
