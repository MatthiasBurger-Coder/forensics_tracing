package de.burger.forensics.plugin.btmgen.gradle;

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

    @Inject
    public BtmGenExtension(ObjectFactory objects) {
        this.sourceRoot = objects.property(File.class);
        this.outputFile = objects.property(File.class);
        this.includes = objects.property(String.class);
    }

    /** Strategy registry used by tasks to render rules. */
    public StrategyRegistry getRegistry() { return registry; }
    public void setRegistry(StrategyRegistry registry) { this.registry = registry; }

    /** Where to scan sources (root folder). */
    public Property<@NotNull File> getSourceRoot() { return sourceRoot; }

    /** Output .btm file location. */
    public Property<@NotNull File> getOutputFile() { return outputFile; }

    public Property<@NotNull String> getIncludes() { return includes; }
}
