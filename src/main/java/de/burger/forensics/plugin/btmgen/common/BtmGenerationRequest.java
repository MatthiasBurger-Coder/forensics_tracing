package de.burger.forensics.plugin.btmgen.common;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Build-tool-neutral input model for Byteman rule generation.
 */
public record BtmGenerationRequest(
        List<Path> sourceRoots,
        Path outputFile,
        Path cacheDatabaseFile,
        Path profileReportFile,
        boolean cacheEnabled,
        boolean profilingEnabled,
        boolean strictParsing,
        List<String> includePackages,
        List<String> excludePackages
) {

    public BtmGenerationRequest {
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        outputFile = Objects.requireNonNull(outputFile, "outputFile");
        cacheDatabaseFile = Objects.requireNonNull(cacheDatabaseFile, "cacheDatabaseFile");
        profileReportFile = Objects.requireNonNull(profileReportFile, "profileReportFile");
        includePackages = List.copyOf(Objects.requireNonNull(includePackages, "includePackages"));
        excludePackages = List.copyOf(Objects.requireNonNull(excludePackages, "excludePackages"));
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
        private boolean profilingEnabled = BtmGenerationDefaults.DEFAULT_PROFILING_ENABLED;
        private boolean strictParsing = BtmGenerationDefaults.DEFAULT_STRICT_PARSING;
        private List<String> includePackages = List.of();
        private List<String> excludePackages = List.of();

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

        public Builder profilingEnabled(boolean profilingEnabled) {
            this.profilingEnabled = profilingEnabled;
            return this;
        }

        public Builder strictParsing(boolean strictParsing) {
            this.strictParsing = strictParsing;
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

        public BtmGenerationRequest build() {
            return new BtmGenerationRequest(
                    sourceRoots,
                    outputFile,
                    cacheDatabaseFile,
                    profileReportFile,
                    cacheEnabled,
                    profilingEnabled,
                    strictParsing,
                    includePackages,
                    excludePackages
            );
        }
    }
}
