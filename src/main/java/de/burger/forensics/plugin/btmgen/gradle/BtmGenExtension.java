package de.burger.forensics.plugin.btmgen.gradle;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import lombok.Getter;
import lombok.Setter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;

/**
 * Extension to configure sourceRoot, outputFile and provide a StrategyRegistry.
 */
@Getter
public abstract class BtmGenExtension {

    /**
     * -- GETTER --
     * Strategy registry used by tasks to render rules.
     */
    @Setter
    private StrategyRegistry registry = StrategyRegistries.defaultRegistry();
    /**
     * -- GETTER --
     * Where to scan sources (root folder).
     */
    private final Property<@NotNull File> sourceRoot;
    /**
     * -- GETTER --
     * Output .btm file location.
     */
    private final Property<@NotNull File> outputFile;
    private final Property<@NotNull String> includes;
    /**
     * -- GETTER --
     * Fully qualified helper class invoked from generated rules.
     */
    private final Property<@NotNull String> helperFqn;
    /**
     * -- GETTER --
     * Minimum number of branches required per method to include rules.
     */
    private final Property<@NotNull Integer> minBranchesPerMethod;

    @Inject
    public BtmGenExtension(ObjectFactory objects) {
        this.sourceRoot = objects.property(File.class);
        this.outputFile = objects.property(File.class);
        this.includes = objects.property(String.class);
        this.helperFqn = objects.property(String.class);
        this.minBranchesPerMethod = objects.property(Integer.class);
        this.sourceRoot.convention(new File("src/main/java"));
        this.outputFile.convention(new File("build/forensics/forensics.btm"));
        this.helperFqn.convention(RuleParams.DEFAULT_HELPER_FQN);
        this.minBranchesPerMethod.convention(2);
    }

}
