package de.burger.forensics.application.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Immutable request describing a rule generation run.
 */
public record GenerationRequest(Path root,
                                String helperFqcn,
                                boolean safeMode,
                                boolean includeEntryExit,
                                List<String> packagePrefixes,
                                List<String> excludedPackagePrefixes,
                                int minBranches,
                                boolean strictConditionValidation,
                                List<String> trackedVariables) {
    public static final String DEFAULT_HELPER_FQCN = "de.burger.forensics.infrastructure.rt.RtTraceHelper";

    public GenerationRequest(Path root,
                             String helperFqcn,
                             boolean safeMode,
                             boolean includeEntryExit,
                             List<String> packagePrefixes,
                             int minBranches,
                             boolean strictConditionValidation,
                             List<String> trackedVariables) {
        this(root,
                helperFqcn,
                safeMode,
                includeEntryExit,
                packagePrefixes,
                List.of(),
                minBranches,
                strictConditionValidation,
                trackedVariables);
    }

    public GenerationRequest {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(helperFqcn, "helperFqcn");
        if (helperFqcn.isBlank()) {
            helperFqcn = DEFAULT_HELPER_FQCN;
        }
        packagePrefixes = packagePrefixes == null ? List.of() : List.copyOf(packagePrefixes);
        excludedPackagePrefixes = excludedPackagePrefixes == null ? List.of() : List.copyOf(excludedPackagePrefixes);
        trackedVariables = trackedVariables == null ? List.of() : List.copyOf(trackedVariables);
    }
}
