package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;

/**
 * Extension to configure sourceRoot, outputFile and provide a StrategyRegistry.
 */
public abstract class BtmGenExtension {

    private StrategyRegistry registry = StrategyRegistries.defaultRegistry();
    private final Property<@NotNull File> sourceRoot;
    private final Property<@NotNull File> outputFile;
    private final Property<@NotNull String> includes;
    private final Property<@NotNull File> sourceRootFile;
    private final Property<@NotNull String> helperFqn;

    @Inject
    public BtmGenExtension(ObjectFactory objects) {
        this.sourceRoot = objects.property(File.class);
        this.outputFile = objects.property(File.class);
        this.includes = objects.property(String.class);
        this.sourceRootFile = objects.property(File.class);
        this.helperFqn = objects.property(String.class);
        this.sourceRoot.convention(new File("src/main/java"));
        this.outputFile.convention(new File("build/forensics/forensics.btm"));
        this.helperFqn.convention(RuleParams.DEFAULT_HELPER_FQN);
    }

    /** Strategy registry used by tasks to render rules. */
    public StrategyRegistry getRegistry() { return registry; }
    public void setRegistry(StrategyRegistry registry) { this.registry = registry; }

    /** Where to scan sources (root folder). */
    public Property<@NotNull File> getSourceRoot() { return sourceRoot; }

    /** Output .btm file location. */
    public Property<@NotNull File> getOutputFile() { return outputFile; }

    public Property<@NotNull String> getIncludes() { return includes; }

    public Property<@NotNull File> getSourceRootFile() { return sourceRootFile; }

    /** Fully qualified helper class invoked from generated rules. */
    public Property<@NotNull String> getHelperFqn() { return helperFqn; }
}
