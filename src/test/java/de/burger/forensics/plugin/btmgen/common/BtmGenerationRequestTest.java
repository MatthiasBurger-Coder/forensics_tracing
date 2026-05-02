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
                .cacheBackend("h2")
                .profilingEnabled(true)
                .strictParsing(true)
                .dependencyAwareInvalidation(false)
                .includePackages(includePackages)
                .excludePackages(excludePackages)
                .helperFqn("com.example.Helper")
                .includeEntryExit(false)
                .minBranchesPerMethod(3)
                .includeTimestampHeader(true)
                .templateRequest(new BtmTemplateRequest("CUSTOM", "com.example.Foo", "bar", "(I)V"))
                .build();

        sourceRoots.add(Path.of("other"));
        includePackages.add("changed");
        excludePackages.add("changed");

        assertEquals(List.of(Path.of("src/main/java")), request.sourceRoots());
        assertEquals(List.of("de.burger"), request.includePackages());
        assertEquals(List.of("de.burger.internal"), request.excludePackages());
        assertThrows(UnsupportedOperationException.class, () -> addSourceRoot(request));
        assertThrows(UnsupportedOperationException.class, () -> addIncludePackage(request));
        assertThrows(UnsupportedOperationException.class, () -> addExcludePackage(request));
        assertTrue(request.cacheEnabled());
        assertEquals("h2", request.cacheBackend());
        assertTrue(request.profilingEnabled());
        assertTrue(request.strictParsing());
        assertFalse(request.dependencyAwareInvalidation());
        assertEquals("com.example.Helper", request.helperFqn());
        assertFalse(request.includeEntryExit());
        assertEquals(3, request.minBranchesPerMethod());
        assertTrue(request.includeTimestampHeader());
        assertTrue(request.templateRequest().isPresent());
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
        assertEquals(BtmGenerationDefaults.DEFAULT_CACHE_BACKEND, request.cacheBackend());
        assertFalse(request.profilingEnabled());
        assertFalse(request.strictParsing());
        assertFalse(request.dependencyAwareInvalidation());
        assertEquals(List.of(), request.includePackages());
        assertEquals(List.of(), request.excludePackages());
        assertEquals(BtmGenerationDefaults.DEFAULT_HELPER_FQN, request.helperFqn());
        assertTrue(request.includeEntryExit());
        assertEquals(BtmGenerationDefaults.DEFAULT_MIN_BRANCHES_PER_METHOD, request.minBranchesPerMethod());
        assertFalse(request.includeTimestampHeader());
        assertTrue(request.templateRequest().isEmpty());
    }

    @Test
    void builderRejectsNegativeBranchThreshold() {
        BtmGenerationRequest.Builder builder = BtmGenerationRequest.builder()
                .sourceRoot(Path.of("src/main/java"))
                .minBranchesPerMethod(-1);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    private static void addSourceRoot(BtmGenerationRequest request) {
        request.sourceRoots().add(Path.of("new"));
    }

    private static void addIncludePackage(BtmGenerationRequest request) {
        request.includePackages().add("new");
    }

    private static void addExcludePackage(BtmGenerationRequest request) {
        request.excludePackages().add("new");
    }
}
