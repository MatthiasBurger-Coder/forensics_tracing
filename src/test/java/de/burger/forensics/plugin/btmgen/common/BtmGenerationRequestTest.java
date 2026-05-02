package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BtmGenerationRequestTest {

    @Test
    void builderCreatesRequestWithDefensiveCopies() {
        List<Path> sourceRoots = new ArrayList<>(List.of(Path.of("src/main/java")));
        List<String> includePackages = new ArrayList<>(List.of("de.burger"));
        List<String> excludePackages = new ArrayList<>(List.of("de.burger.internal"));

        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoots(sourceRoots)
                .outputFile(Path.of("out/rules.btm"))
                .cacheDatabaseFile(Path.of("cache/scan-cache"))
                .profileReportFile(Path.of("profile/report.json"))
                .cacheEnabled(true)
                .profilingEnabled(true)
                .strictParsing(true)
                .includePackages(includePackages)
                .excludePackages(excludePackages)
                .build();

        sourceRoots.add(Path.of("other"));
        includePackages.add("changed");
        excludePackages.add("changed");

        assertEquals(List.of(Path.of("src/main/java")), request.sourceRoots());
        assertEquals(List.of("de.burger"), request.includePackages());
        assertEquals(List.of("de.burger.internal"), request.excludePackages());
        assertThrows(UnsupportedOperationException.class, () -> request.sourceRoots().add(Path.of("new")));
        assertThrows(UnsupportedOperationException.class, () -> request.includePackages().add("new"));
        assertThrows(UnsupportedOperationException.class, () -> request.excludePackages().add("new"));
        assertTrue(request.cacheEnabled());
        assertTrue(request.profilingEnabled());
        assertTrue(request.strictParsing());
    }

    @Test
    void builderUsesDefaultsWhenOptionalValuesAreNotConfigured() {
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoot(Path.of("src/main/java"))
                .build();

        assertEquals(List.of(Path.of("src/main/java")), request.sourceRoots());
        assertEquals(BtmGenerationDefaults.defaultOutputFile(), request.outputFile());
        assertEquals(BtmGenerationDefaults.defaultCacheDatabaseFile(), request.cacheDatabaseFile());
        assertEquals(BtmGenerationDefaults.defaultProfileReportFile(), request.profileReportFile());
        assertFalse(request.cacheEnabled());
        assertFalse(request.profilingEnabled());
        assertFalse(request.strictParsing());
        assertEquals(List.of(), request.includePackages());
        assertEquals(List.of(), request.excludePackages());
    }
}
