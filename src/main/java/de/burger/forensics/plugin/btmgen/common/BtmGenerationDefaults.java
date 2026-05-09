package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.domain.model.analysis.AnalysisStoreCleanupPolicy;

import java.nio.file.Path;

/**
 * Build-tool-neutral defaults for Byteman rule generation.
 */
public final class BtmGenerationDefaults {

    public static final String DEFAULT_OUTPUT_FILE_NAME = "forensics.btm";
    public static final String DEFAULT_PROFILE_REPORT_FILE_NAME = "scan-profile.json";
    public static final String DEFAULT_CACHE_DATABASE_FILE_NAME = "scan-cache";
    public static final String DEFAULT_MANIFEST_FILE_NAME = "manifest.json";
    public static final String DEFAULT_CHECKSUMS_FILE_NAME = "checksums.sha256";
    public static final String DEFAULT_ANALYSIS_STORE_DATABASE_FILE_NAME = "analysis-store";
    public static final String DEFAULT_CACHE_BACKEND = "h2";
    public static final String DEFAULT_TEMPLATE_ID = "METHOD_ENTER";
    public static final String DEFAULT_HELPER_FQN = RuleParams.DEFAULT_HELPER_FQN;
    public static final boolean DEFAULT_CACHE_ENABLED = false;
    public static final boolean DEFAULT_ANALYSIS_STORE_ENABLED = false;
    public static final boolean DEFAULT_PROFILING_ENABLED = false;
    public static final boolean DEFAULT_STRICT_PARSING = false;
    public static final boolean DEFAULT_STRICT_CONDITION_VALIDATION = false;
    public static final boolean DEFAULT_DEPENDENCY_AWARE_INVALIDATION = false;
    public static final boolean DEFAULT_INCLUDE_ENTRY_EXIT = true;
    public static final boolean DEFAULT_INCLUDE_TIMESTAMP_HEADER = false;
    public static final int DEFAULT_MIN_BRANCHES_PER_METHOD = 2;

    private static final Path FORENSICS_DIRECTORY = Path.of("forensics");
    private static final Path CACHE_DIRECTORY = FORENSICS_DIRECTORY.resolve("cache");
    private static final Path ANALYSIS_STORE_DIRECTORY = FORENSICS_DIRECTORY.resolve("analysis-store");

    private BtmGenerationDefaults() {
    }

    public static Path defaultOutputFile() {
        return FORENSICS_DIRECTORY.resolve(DEFAULT_OUTPUT_FILE_NAME);
    }

    public static Path defaultProfileReportFile() {
        return FORENSICS_DIRECTORY.resolve(DEFAULT_PROFILE_REPORT_FILE_NAME);
    }

    public static Path defaultCacheDatabaseFile() {
        return CACHE_DIRECTORY.resolve(DEFAULT_CACHE_DATABASE_FILE_NAME);
    }

    public static Path defaultAnalysisStoreDirectory() {
        return ANALYSIS_STORE_DIRECTORY;
    }

    public static Path defaultManifestFile() {
        return FORENSICS_DIRECTORY.resolve(DEFAULT_MANIFEST_FILE_NAME);
    }

    public static Path defaultChecksumsFile() {
        return FORENSICS_DIRECTORY.resolve(DEFAULT_CHECKSUMS_FILE_NAME);
    }

    public static String defaultCleanupPolicy() {
        return AnalysisStoreCleanupPolicy.DEFAULT.name();
    }
}
