package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BtmGenerationDefaultsTest {

    @Test
    void defaultsExposeRelativeFileLocations() {
        assertEquals(Path.of("forensics", "forensics.btm"), BtmGenerationDefaults.defaultOutputFile());
        assertEquals(Path.of("forensics", "scan-profile.json"), BtmGenerationDefaults.defaultProfileReportFile());
        assertEquals(Path.of("forensics", "cache", "scan-cache"), BtmGenerationDefaults.defaultCacheDatabaseFile());
    }

    @Test
    void defaultsExposeDisabledOptionalFeatures() {
        assertFalse(BtmGenerationDefaults.DEFAULT_CACHE_ENABLED);
        assertFalse(BtmGenerationDefaults.DEFAULT_PROFILING_ENABLED);
        assertFalse(BtmGenerationDefaults.DEFAULT_STRICT_PARSING);
    }
}
