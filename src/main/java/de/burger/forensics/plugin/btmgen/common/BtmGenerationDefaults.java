package de.burger.forensics.plugin.btmgen.common;

import java.nio.file.Path;

/**
 * Build-tool-neutral defaults for Byteman rule generation.
 */
public final class BtmGenerationDefaults {

    public static final String DEFAULT_OUTPUT_FILE_NAME = "forensics.btm";
    public static final String DEFAULT_PROFILE_REPORT_FILE_NAME = "scan-profile.json";
    public static final String DEFAULT_CACHE_DATABASE_FILE_NAME = "scan-cache";
    public static final boolean DEFAULT_CACHE_ENABLED = false;
    public static final boolean DEFAULT_PROFILING_ENABLED = false;
    public static final boolean DEFAULT_STRICT_PARSING = false;

    private static final Path FORENSICS_DIRECTORY = Path.of("forensics");
    private static final Path CACHE_DIRECTORY = FORENSICS_DIRECTORY.resolve("cache");

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
}
