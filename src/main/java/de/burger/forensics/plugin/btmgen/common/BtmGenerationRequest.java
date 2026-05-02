package de.burger.forensics.plugin.btmgen.common;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Build-tool-neutral input model for Byteman rule generation.
 */
public record BtmGenerationRequest(
        List<Path> sourceRoots,
        Path outputFile,
        Path cacheDatabaseFile,
        Path profileReportFile,
        boolean cacheEnabled,
        String cacheBackend,
        boolean profilingEnabled,
        boolean strictParsing,
        boolean dependencyAwareInvalidation,
        List<String> includePackages,
        List<String> excludePackages,
        String helperFqn,
        boolean includeEntryExit,
        int minBranchesPerMethod,
        boolean includeTimestampHeader,
        Optional<BtmTemplateRequest> templateRequest
) {

    public BtmGenerationRequest {
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        outputFile = Objects.requireNonNull(outputFile, "outputFile");
        cacheDatabaseFile = Objects.requireNonNull(cacheDatabaseFile, "cacheDatabaseFile");
        profileReportFile = Objects.requireNonNull(profileReportFile, "profileReportFile");
        cacheBackend = Objects.requireNonNull(cacheBackend, "cacheBackend");
        includePackages = List.copyOf(Objects.requireNonNull(includePackages, "includePackages"));
        excludePackages = List.copyOf(Objects.requireNonNull(excludePackages, "excludePackages"));
        helperFqn = Objects.requireNonNull(helperFqn, "helperFqn");
        if (minBranchesPerMethod < 0) {
            throw new IllegalArgumentException("minBranchesPerMethod must not be negative");
        }
        templateRequest = Objects.requireNonNull(templateRequest, "templateRequest");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<Path> sourceRoots = List.of();
        private Path outputFile = BtmGenerationDefaults.defaultOutputFile();
        private Path cacheDatabaseFile = BtmGenerationDefaults.defaultCacheDatabaseFile();
        private Path profileReportFile = BtmGenerationDefaults.defaultProfileReportFile();
        private boolean cacheEnabled = BtmGenerationDefaults.DEFAULT_CACHE_ENABLED;
        private String cacheBackend = BtmGenerationDefaults.DEFAULT_CACHE_BACKEND;
        private boolean profilingEnabled = BtmGenerationDefaults.DEFAULT_PROFILING_ENABLED;
        private boolean strictParsing = BtmGenerationDefaults.DEFAULT_STRICT_PARSING;
        private boolean dependencyAwareInvalidation = BtmGenerationDefaults.DEFAULT_DEPENDENCY_AWARE_INVALIDATION;
        private List<String> includePackages = List.of();
        private List<String> excludePackages = List.of();
        private String helperFqn = BtmGenerationDefaults.DEFAULT_HELPER_FQN;
        private boolean includeEntryExit = BtmGenerationDefaults.DEFAULT_INCLUDE_ENTRY_EXIT;
        private int minBranchesPerMethod = BtmGenerationDefaults.DEFAULT_MIN_BRANCHES_PER_METHOD;
        private boolean includeTimestampHeader = BtmGenerationDefaults.DEFAULT_INCLUDE_TIMESTAMP_HEADER;
        private Optional<BtmTemplateRequest> templateRequest = Optional.empty();

        public Builder sourceRoot(Path sourceRoot) {
            this.sourceRoots = List.of(Objects.requireNonNull(sourceRoot, "sourceRoot"));
            return this;
        }

        public Builder sourceRoots(List<Path> sourceRoots) {
            this.sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
            return this;
        }

        public Builder outputFile(Path outputFile) {
            this.outputFile = Objects.requireNonNull(outputFile, "outputFile");
            return this;
        }

        public Builder cacheDatabaseFile(Path cacheDatabaseFile) {
            this.cacheDatabaseFile = Objects.requireNonNull(cacheDatabaseFile, "cacheDatabaseFile");
            return this;
        }

        public Builder profileReportFile(Path profileReportFile) {
            this.profileReportFile = Objects.requireNonNull(profileReportFile, "profileReportFile");
            return this;
        }

        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        public Builder cacheBackend(String cacheBackend) {
            this.cacheBackend = Objects.requireNonNull(cacheBackend, "cacheBackend");
            return this;
        }

        public Builder profilingEnabled(boolean profilingEnabled) {
            this.profilingEnabled = profilingEnabled;
            return this;
        }

        public Builder strictParsing(boolean strictParsing) {
            this.strictParsing = strictParsing;
            return this;
        }

        public Builder dependencyAwareInvalidation(boolean dependencyAwareInvalidation) {
            this.dependencyAwareInvalidation = dependencyAwareInvalidation;
            return this;
        }

        public Builder includePackages(List<String> includePackages) {
            this.includePackages = List.copyOf(Objects.requireNonNull(includePackages, "includePackages"));
            return this;
        }

        public Builder excludePackages(List<String> excludePackages) {
            this.excludePackages = List.copyOf(Objects.requireNonNull(excludePackages, "excludePackages"));
            return this;
        }

        public Builder helperFqn(String helperFqn) {
            this.helperFqn = Objects.requireNonNull(helperFqn, "helperFqn");
            return this;
        }

        public Builder includeEntryExit(boolean includeEntryExit) {
            this.includeEntryExit = includeEntryExit;
            return this;
        }

        public Builder minBranchesPerMethod(int minBranchesPerMethod) {
            this.minBranchesPerMethod = minBranchesPerMethod;
            return this;
        }

        public Builder includeTimestampHeader(boolean includeTimestampHeader) {
            this.includeTimestampHeader = includeTimestampHeader;
            return this;
        }

        public Builder templateRequest(BtmTemplateRequest templateRequest) {
            this.templateRequest = Optional.of(Objects.requireNonNull(templateRequest, "templateRequest"));
            return this;
        }

        public Builder noTemplateRequest() {
            this.templateRequest = Optional.empty();
            return this;
        }

        public BtmGenerationRequest build() {
            return new BtmGenerationRequest(
                    sourceRoots,
                    outputFile,
                    cacheDatabaseFile,
                    profileReportFile,
                    cacheEnabled,
                    cacheBackend,
                    profilingEnabled,
                    strictParsing,
                    dependencyAwareInvalidation,
                    includePackages,
                    excludePackages,
                    helperFqn,
                    includeEntryExit,
                    minBranchesPerMethod,
                    includeTimestampHeader,
                    templateRequest
            );
        }
    }
}
