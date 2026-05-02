package de.burger.forensics.plugin.btmgen.maven;

import de.burger.forensics.plugin.btmgen.common.BtmGenerationException;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRequest;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationResult;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationRunner;
import de.burger.forensics.plugin.btmgen.common.BtmGenerationDefaults;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven goal that delegates BTM generation to the shared build-tool-neutral runner.
 */
@Mojo(
        name = "btmgen",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresProject = true,
        requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true
)
public class BtmGenMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forensics.sourceRoot")
    private File sourceRoot;

    @Parameter(property = "forensics.outputFile", defaultValue = "${project.build.directory}/forensics/generated.btm")
    private File outputFile;

    @Parameter(property = "forensics.cacheEnabled", defaultValue = "false")
    private boolean cacheEnabled;

    @Parameter(property = "forensics.cacheBackend", defaultValue = "h2")
    private String cacheBackend;

    @Parameter(property = "forensics.cacheDatabaseFile", defaultValue = "${project.build.directory}/forensics/cache/scan-cache")
    private File cacheDatabaseFile;

    @Parameter(property = "forensics.profilingEnabled", defaultValue = "false")
    private boolean profilingEnabled;

    @Parameter(property = "forensics.profileReportFile", defaultValue = "${project.build.directory}/forensics/scan-profile.json")
    private File profileReportFile;

    @Parameter(property = "forensics.strictParsing", defaultValue = "false")
    private boolean strictParsing;

    @Parameter(property = "forensics.dependencyAwareInvalidation", defaultValue = "false")
    private boolean dependencyAwareInvalidation;

    @Parameter(property = "forensics.includePackages")
    private String includePackages;

    @Parameter(property = "forensics.excludePackages")
    private String excludePackages;

    @Parameter(property = "forensics.includeTests", defaultValue = "false")
    private boolean includeTests;

    @Parameter(property = "forensics.helperFqn", defaultValue = BtmGenerationDefaults.DEFAULT_HELPER_FQN)
    private String helperFqn;

    @Parameter(property = "forensics.includeEntryExit", defaultValue = "true")
    private boolean includeEntryExit;

    @Parameter(property = "forensics.minBranchesPerMethod", defaultValue = "2")
    private int minBranchesPerMethod;

    @Parameter(property = "forensics.includeTimestampHeader", defaultValue = "false")
    private boolean includeTimestampHeader;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            BtmGenerationRequest request = parameters().toGenerationRequest();
            BtmGenerationResult result = new BtmGenerationRunner(
                    StrategyRegistries.defaultRegistry(),
                    new MavenLogAdapter(getLog())
            ).generate(request);
            getLog().info("Generated " + result.generatedRuleCount() + " BTM rules.");
        } catch (IllegalArgumentException | BtmGenerationException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    MavenBtmGenParameters parameters() {
        return new MavenBtmGenParameters(
                project,
                sourceRoot,
                outputFile,
                cacheEnabled,
                cacheBackend,
                cacheDatabaseFile,
                profilingEnabled,
                profileReportFile,
                strictParsing,
                dependencyAwareInvalidation,
                includePackages,
                excludePackages,
                includeTests,
                helperFqn,
                includeEntryExit,
                minBranchesPerMethod,
                includeTimestampHeader
        );
    }
}
