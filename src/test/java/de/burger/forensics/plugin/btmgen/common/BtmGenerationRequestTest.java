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
                .analysisStoreDirectory(Path.of("forensics/analysis-store"))
                .profileReportFile(Path.of("profile/report.json"))
                .cacheEnabled(true)
                .analysisStoreEnabled(true)
                .cacheBackend("h2")
                .profilingEnabled(true)
                .strictParsing(true)
                .strictConditionValidation(true)
                .dependencyAwareInvalidation(false)
                .includePackages(includePackages)
                .excludePackages(excludePackages)
                .helperFqn("com.example.Helper")
                .includeEntryExit(false)
                .minBranchesPerMethod(3)
                .includeTimestampHeader(true)
                .cleanupPolicy("KEEP_ALWAYS")
                .projectKey("demo")
                .pluginVersion("1.0")
                .manifestFile(Path.of("forensics/manifest.json"))
                .checksumsFile(Path.of("forensics/checksums.sha256"))
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
        assertTrue(request.analysisStoreEnabled());
        assertEquals("h2", request.cacheBackend());
        assertTrue(request.profilingEnabled());
        assertTrue(request.strictParsing());
        assertTrue(request.strictConditionValidation());
        assertFalse(request.dependencyAwareInvalidation());
        assertEquals("com.example.Helper", request.helperFqn());
        assertFalse(request.includeEntryExit());
        assertEquals(3, request.minBranchesPerMethod());
        assertTrue(request.includeTimestampHeader());
        assertEquals("KEEP_ALWAYS", request.cleanupPolicy());
        assertEquals("demo", request.projectKey());
        assertEquals("1.0", request.pluginVersion());
        assertEquals(Path.of("forensics/manifest.json"), request.manifestFile());
        assertEquals(Path.of("forensics/checksums.sha256"), request.checksumsFile());
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
        assertEquals(BtmGenerationDefaults.defaultAnalysisStoreDirectory(), request.analysisStoreDirectory());
        assertEquals(BtmGenerationDefaults.defaultProfileReportFile(), request.profileReportFile());
        assertFalse(request.cacheEnabled());
        assertFalse(request.analysisStoreEnabled());
        assertEquals(BtmGenerationDefaults.DEFAULT_CACHE_BACKEND, request.cacheBackend());
        assertFalse(request.profilingEnabled());
        assertFalse(request.strictParsing());
        assertFalse(request.strictConditionValidation());
        assertFalse(request.dependencyAwareInvalidation());
        assertEquals(List.of(), request.includePackages());
        assertEquals(List.of(), request.excludePackages());
        assertEquals(BtmGenerationDefaults.DEFAULT_HELPER_FQN, request.helperFqn());
        assertTrue(request.includeEntryExit());
        assertEquals(BtmGenerationDefaults.DEFAULT_MIN_BRANCHES_PER_METHOD, request.minBranchesPerMethod());
        assertFalse(request.includeTimestampHeader());
        assertEquals(BtmGenerationDefaults.defaultCleanupPolicy(), request.cleanupPolicy());
        assertEquals("UNKNOWN", request.projectKey());
        assertEquals("UNKNOWN", request.pluginVersion());
        assertEquals(BtmGenerationDefaults.defaultManifestFile(), request.manifestFile());
        assertEquals(BtmGenerationDefaults.defaultChecksumsFile(), request.checksumsFile());
        assertTrue(request.templateRequest().isEmpty());
    }

    @Test
    void builderRejectsNegativeBranchThreshold() {
        BtmGenerationRequest.Builder builder = BtmGenerationRequest.builder()
                .sourceRoot(Path.of("src/main/java"))
                .minBranchesPerMethod(-1);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void builderCanClearTemplateRequest() {
        BtmGenerationRequest request = BtmGenerationRequest.builder()
                .sourceRoot(Path.of("src/main/java"))
                .templateRequest(new BtmTemplateRequest("CUSTOM", "com.example.Foo", "bar", "(I)V"))
                .noTemplateRequest()
                .build();

        assertTrue(request.templateRequest().isEmpty());
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
