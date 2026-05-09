package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Path;
import java.util.List;

/**
 * Shared Maven reactor source-root mapping for aggregate BTM goals.
 */
abstract class AbstractAggregateBtmGenerationMojo extends AbstractBtmGenerationMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Override
    MavenBtmGenParameters parameters() {
        return parameters(reactorRoots());
    }

    MavenBtmGenParameters parameters(String effectiveCleanupPolicy) {
        return parameters(reactorRoots(), effectiveCleanupPolicy);
    }

    private List<Path> reactorRoots() {
        return hasExplicitSourceRoots()
                ? List.of()
                : new MavenReactorSourceRootCollector().collect(session, project(), includeTests());
    }
}
