package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.adapters.javaparser.CachedJavaParserScanner;
import de.burger.forensics.adapters.javaparser.JavaParserScanner;
import de.burger.forensics.adapters.filesystem.AnalysisManifestWriter;
import de.burger.forensics.adapters.filesystem.ArtifactChecksumService;
import de.burger.forensics.adapters.filesystem.ChecksumFileWriter;
import de.burger.forensics.adapters.persistence.h2.H2AnalysisStoreAdapter;
import de.burger.forensics.adapters.persistence.h2.H2ScanCacheAdapter;
import de.burger.forensics.application.AnalysisContext;
import de.burger.forensics.adaptersupport.javaparser.DefaultSourceFingerprintPort;
import de.burger.forensics.application.service.ConditionValidationException;
import de.burger.forensics.application.service.GenerateRulesUseCase;
import de.burger.forensics.application.service.GenerationRequest;
import de.burger.forensics.application.service.RuleGenerationResult;
import de.burger.forensics.application.service.SourceFingerprintResult;
import de.burger.forensics.application.service.SourceFingerprintService;
import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.analysis.AnalysisRunId;
import de.burger.forensics.domain.model.analysis.AnalysisRunStatus;
import de.burger.forensics.domain.model.analysis.AnalysisSchemaVersion;
import de.burger.forensics.domain.model.analysis.AnalysisStoreCleanupPolicy;
import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.analysis.BuildId;
import de.burger.forensics.domain.model.analysis.BuildIdentity;
import de.burger.forensics.domain.model.cache.ScanPhase;
import de.burger.forensics.domain.model.cache.ScanProfile;
import de.burger.forensics.domain.port.out.CodeScanPort;
import de.burger.forensics.domain.port.out.LogPort;
import de.burger.forensics.domain.port.out.RuleRenderPort;
import de.burger.forensics.domain.port.out.ScanProfileSinkPort;
import de.burger.forensics.domain.strategy.DefaultStrategyFactory;
import de.burger.forensics.domain.validation.ConditionValidationReport;
import de.burger.forensics.plugin.adapters.JsonScanProfileSinkAdapter;
import de.burger.forensics.plugin.adapters.SystemClockAdapter;
import de.burger.forensics.plugin.btmgen.internal.BytemanRuleRenderAdapter;
import de.burger.forensics.plugin.btmgen.render.BtmHeaderRenderer;
import de.burger.forensics.plugin.btmgen.render.BytemanRuleRenderer;
import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import de.burger.forensics.plugin.btmgen.writer.BtmFileWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Build-tool-neutral orchestration for scanning sources and writing Byteman rules.
 */
public final class BtmGenerationRunner {

    private final StrategyRegistry registry;
    private final PluginLogPort log;

    public BtmGenerationRunner() {
        this(StrategyRegistries.defaultRegistry(), NoOpPluginLogPort.INSTANCE);
    }

    public BtmGenerationRunner(StrategyRegistry registry, PluginLogPort log) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.log = Objects.requireNonNull(log, "log");
    }

    public BtmGenerationResult generate(BtmGenerationRequest request) {
        Objects.requireNonNull(request, "request");
        Path outputFile = request.outputFile();
        BytemanRuleRenderer renderer = BytemanRuleRenderer.of(registry);
        ScanProfileCollector profileCollector = new ScanProfileCollector(request.profilingEnabled());
        SourceFingerprintResult sourceFingerprint = request.analysisStoreEnabled()
                ? new SourceFingerprintService().fingerprint(request.sourceRoots())
                : null;

        createOutputDirectory(outputFile);

        RunOutput output = request.templateRequest()
                .map(template -> new RunOutput(
                        List.of(),
                        renderTemplate(renderer, request, template, profileCollector),
                        AnalysisContext.builder().build(),
                        ConditionValidationReport.empty()))
                .orElseGet(() -> scanSources(renderer, request, profileCollector));

        List<String> uniqueRules = new ArrayList<>(new LinkedHashSet<>(output.renderedRules()));
        List<String> dedupedRuleNames = dedupeRuleHeaders(uniqueRules);
        BuildIdentity identity = null;
        if (request.analysisStoreEnabled()) {
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            identity = buildIdentity(request, sourceFingerprint, rulesFingerprint(dedupedRuleNames));
            persistAnalysis(request, output, sourceFingerprint, dedupedRuleNames, identity, profileCollector);
        } else {
            profileCollector.measure(ScanPhase.BTM_FILE_WRITING, () -> {
                writeRules(request, dedupedRuleNames);
                return null;
            });
        }

        ScanProfile profile = profileCollector.profile();
        publishProfile(request, profile, output.validationReport());
        log.info("Generated " + dedupedRuleNames.size() + " rules -> " + outputFile.toAbsolutePath());

        return new BtmGenerationResult(
                outputFile,
                request.profileReportFile(),
                dedupedRuleNames.size(),
                profile.totalFiles(),
                profile.parsedFiles(),
                profile.failedFiles(),
                profile.cacheHitFiles(),
                profile.cacheMissFiles(),
                output.validationReport()
        );
    }

    static List<String> dedupeRuleHeaders(List<String> rules) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> out = new ArrayList<>(rules.size());
        for (String rule : rules) {
            RuleHeader header = findRuleHeader(rule);
            String rewrittenRule = rule;
            if (header != null) {
                String originalName = header.name();
                int index = seen.merge(originalName, 1, Integer::sum);
                if (index > 1) {
                    String replacement = header.prefix() + originalName + "_" + index;
                    rewrittenRule = rule.substring(0, header.startIndex()) + replacement + rule.substring(header.endIndex());
                }
            }
            out.add(rewrittenRule);
        }
        return out;
    }

    private List<String> renderTemplate(BytemanRuleRenderer renderer,
                                        BtmGenerationRequest request,
                                        BtmTemplateRequest template,
                                        ScanProfileCollector profileCollector) {
        RuleParams params = new RuleParams(
                template.templateId(),
                template.className(),
                template.methodName(),
                template.methodDesc().orElse(null),
                template.className() + "#" + template.methodName(),
                null,
                null,
                helperFqn(request)
        );
        return List.of(profileCollector.measure(ScanPhase.RULE_RENDERING,
                () -> renderer.render(template.templateId(), params)));
    }

    private RunOutput scanSources(BytemanRuleRenderer renderer,
                                  BtmGenerationRequest request,
                                  ScanProfileCollector profileCollector) {
        RuleRenderPort ruleRenderer = profileCollector.wrap(new BytemanRuleRenderAdapter(renderer));
        GenerateRulesUseCase useCase = new GenerateRulesUseCase(
                createScanner(request, profileCollector),
                ruleRenderer,
                new SystemClockAdapter(),
                new DomainLogAdapter(log),
                new DefaultStrategyFactory()
        );
        List<Rule> allDomainRules = new ArrayList<>();
        List<String> allRules = new ArrayList<>();
        AnalysisContext mergedContext = AnalysisContext.builder().build();
        ConditionValidationReport validationReport = ConditionValidationReport.empty();
        for (Path srcRoot : request.sourceRoots()) {
            log.info("Scanning sources in " + srcRoot.toAbsolutePath());
            GenerationRequest generationRequest = new GenerationRequest(
                    srcRoot,
                    helperFqn(request),
                    false,
                    request.includeEntryExit(),
                    request.includePackages(),
                    request.minBranchesPerMethod(),
                    request.strictConditionValidation(),
                    List.of()
            );
            RuleGenerationResult result;
            try {
                result = useCase.generate(generationRequest);
            } catch (ConditionValidationException exception) {
                throw new BtmGenerationException(exception.getMessage(), exception);
            }
            allDomainRules.addAll(result.rules());
            allRules.addAll(result.renderedRules());
            result.context().getEvents().forEach(mergedContext::addEvent);
            result.context().getMethodEntries().forEach(mergedContext::addMethodEntry);
            validationReport = validationReport.merge(result.validationReport());
        }
        return new RunOutput(allDomainRules, allRules, mergedContext, validationReport);
    }

    private void persistAnalysis(BtmGenerationRequest request,
                                 RunOutput output,
                                 SourceFingerprintResult sourceFingerprint,
                                 List<String> dedupedRules,
                                 BuildIdentity identity,
                                 ScanProfileCollector profileCollector) {
        Path analysisStoreDirectory = request.analysisStoreDirectory();
        Path databaseFile = analysisStoreDirectory.resolve(BtmGenerationDefaults.DEFAULT_ANALYSIS_STORE_DATABASE_FILE_NAME);
        ArtifactChecksumService checksumService = new ArtifactChecksumService();
        ArtifactChecksum btmChecksum = null;
        boolean success = false;
        try (H2AnalysisStoreAdapter store = new H2AnalysisStoreAdapter(databaseFile)) {
            btmChecksum = persistAnalysisRun(
                    request,
                    output,
                    sourceFingerprint,
                    dedupedRules,
                    identity,
                    profileCollector,
                    checksumService,
                    store);
            success = true;
        } finally {
            if (success && btmChecksum != null) {
                writeAnalysisArtifacts(request, identity, btmChecksum, checksumService);
            }
            applyCleanupPolicy(request, success);
        }
    }

    private ArtifactChecksum persistAnalysisRun(BtmGenerationRequest request,
                                                RunOutput output,
                                                SourceFingerprintResult sourceFingerprint,
                                                List<String> dedupedRules,
                                                BuildIdentity identity,
                                                ScanProfileCollector profileCollector,
                                                ArtifactChecksumService checksumService,
                                                H2AnalysisStoreAdapter store) {
        try {
            store.initializeSchema();
            store.createAnalysisRun(identity);
            store.updateAnalysisRunStatus(identity.analysisRunId(), AnalysisRunStatus.SCANNING);
            store.storeSourceFiles(identity.analysisRunId(), sourceFingerprint.sourceFiles());
            store.storeMethods(identity.analysisRunId(), output.context().getMethodEntries());
            store.storeScanEvents(identity.analysisRunId(), output.context().getEvents());
            store.storeRules(
                    identity.analysisRunId(),
                    output.domainRules(),
                    renderedRulesByRuleId(output.domainRules(), output.renderedRules()));
            profileCollector.measure(ScanPhase.BTM_FILE_WRITING, () -> {
                writeRules(request, dedupedRules, identity);
                return null;
            });
            store.updateAnalysisRunStatus(identity.analysisRunId(), AnalysisRunStatus.BTM_GENERATED);
            ArtifactChecksum btmChecksum = checksumService.checksumFile(
                    analysisBaseDirectory(request),
                    request.outputFile(),
                    "byteman-rules");
            store.storeArtifactChecksums(identity.analysisRunId(), List.of(btmChecksum));
            store.updateAnalysisRunStatus(identity.analysisRunId(), AnalysisRunStatus.COMPLETED);
            return btmChecksum;
        } catch (RuntimeException exception) {
            markFailed(store, identity.analysisRunId());
            throw exception;
        }
    }

    private void writeAnalysisArtifacts(BtmGenerationRequest request,
                                        BuildIdentity identity,
                                        ArtifactChecksum btmChecksum,
                                        ArtifactChecksumService checksumService) {
        Path baseDirectory = analysisBaseDirectory(request);
        ArtifactChecksum storeChecksum = checksumService.checksumDirectory(
                baseDirectory,
                request.analysisStoreDirectory(),
                "h2-analysis-store");
        new AnalysisManifestWriter().write(request.manifestFile(), identity, List.of(btmChecksum, storeChecksum));
        ArtifactChecksum manifestChecksum = checksumService.checksumFile(
                baseDirectory,
                request.manifestFile(),
                "analysis-manifest");
        List<ArtifactChecksum> checksumEntries = new ArrayList<>();
        checksumEntries.add(btmChecksum);
        checksumEntries.add(manifestChecksum);
        checksumEntries.addAll(checksumService.checksumFiles(
                baseDirectory,
                request.analysisStoreDirectory(),
                "h2-analysis-store-file"));
        new ChecksumFileWriter().write(request.checksumsFile(), checksumEntries);
    }

    private void markFailed(H2AnalysisStoreAdapter store, AnalysisRunId analysisRunId) {
        try {
            store.updateAnalysisRunStatus(analysisRunId, AnalysisRunStatus.FAILED);
        } catch (RuntimeException failure) {
            log.warn("Failed to mark analysis run as failed: " + failure.getMessage());
        }
    }

    private void applyCleanupPolicy(BtmGenerationRequest request, boolean success) {
        AnalysisStoreCleanupPolicy policy = AnalysisStoreCleanupPolicy.from(request.cleanupPolicy());
        boolean delete = success ? policy.shouldDeleteAfterSuccess() : policy.shouldDeleteAfterFailure();
        if (!delete) {
            return;
        }
        try {
            deleteRecursively(request.analysisStoreDirectory());
        } catch (IOException e) {
            throw new BtmGenerationException("Failed to clean analysis store " + request.analysisStoreDirectory(), e);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream.sorted((left, right) -> right.compareTo(left)).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Map<String, String> renderedRulesByRuleId(List<Rule> rules, List<String> renderedRules) {
        Map<String, String> rendered = new HashMap<>();
        for (int index = 0; index < rules.size(); index++) {
            String ruleBody = index < renderedRules.size() ? renderedRules.get(index) : null;
            if (ruleBody != null) {
                rendered.putIfAbsent(rules.get(index).id().value(), ruleBody);
            }
        }
        return rendered;
    }

    private static BuildIdentity buildIdentity(BtmGenerationRequest request,
                                               SourceFingerprintResult sourceFingerprint,
                                               String btmRulesFingerprint) {
        String projectKey = request.projectKey().isBlank() ? BuildIdentity.UNKNOWN : request.projectKey();
        String seed = projectKey + "|" + sourceFingerprint.sourceFingerprint().value() + "|" + btmRulesFingerprint
                + "|" + request.pluginVersion();
        BuildId buildId = new BuildId("sha256:" + sha256(seed));
        return new BuildIdentity(
                projectKey,
                AnalysisRunId.deterministic(buildId.value()),
                buildId,
                sourceFingerprint.sourceFingerprint(),
                BuildIdentity.NOT_COMPUTED,
                btmRulesFingerprint,
                BuildIdentity.NOT_COMPUTED,
                request.pluginVersion(),
                AnalysisSchemaVersion.CURRENT,
                Instant.EPOCH);
    }

    private static String rulesFingerprint(List<String> rules) {
        return "sha256:" + sha256(String.join("\n", rules));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static Path analysisBaseDirectory(BtmGenerationRequest request) {
        Path parent = request.manifestFile().toAbsolutePath().normalize().getParent();
        return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
    }

    private CodeScanPort createScanner(BtmGenerationRequest request, ScanProfileCollector profileCollector) {
        if (!request.cacheEnabled()) {
            return new JavaParserScanner();
        }
        if (!BtmGenerationDefaults.DEFAULT_CACHE_BACKEND.equalsIgnoreCase(request.cacheBackend())) {
            throw new BtmGenerationException("Unsupported parser scan cache backend: " + request.cacheBackend());
        }
        if (request.dependencyAwareInvalidation()) {
            throw new BtmGenerationException("Dependency-aware cache invalidation is not implemented yet.");
        }
        return new CachedJavaParserScanner(
                new H2ScanCacheAdapter(request.cacheDatabaseFile()),
                new DefaultSourceFingerprintPort(),
                request.profilingEnabled() ? profileCollector : null,
                request.strictParsing()
        );
    }

    private void publishProfile(BtmGenerationRequest request, ScanProfile profile, ConditionValidationReport validationReport) {
        if (!request.profilingEnabled()) {
            return;
        }
        new JsonScanProfileSinkAdapter(request.profileReportFile()).publish(profile, validationReport);
    }

    private String helperFqn(BtmGenerationRequest request) {
        String helper = request.helperFqn();
        return helper.isBlank() ? BtmGenerationDefaults.DEFAULT_HELPER_FQN : helper;
    }

    private void writeRules(BtmGenerationRequest request, List<String> rules) {
        writeRules(request, rules, null);
    }

    private void writeRules(BtmGenerationRequest request, List<String> rules, BuildIdentity identity) {
        Path outputFile = request.outputFile();
        try {
            List<String> header = identity == null ? List.of() : new BtmHeaderRenderer().render(identity);
            createWriter(outputFile, request.includeTimestampHeader()).write(header, rules);
        } catch (UncheckedIOException e) {
            throw new BtmGenerationException("Failed writing BTM file " + outputFile, e);
        }
    }

    private void createOutputDirectory(Path outputFile) {
        Path parent = outputFile.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new BtmGenerationException("Failed to create output directory for " + outputFile, e);
        }
    }

    private static BtmFileWriter createWriter(Path outFile, boolean includeTimestampHeader) {
        try {
            return new BtmFileWriter(Clock.systemDefaultZone(), outFile, includeTimestampHeader);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            return new BtmFileWriter(outFile);
        }
    }

    private static RuleHeader findRuleHeader(String rule) {
        int lineStart = 0;
        while (lineStart < rule.length()) {
            int lineEnd = findLineEnd(rule, lineStart);
            RuleHeader header = parseRuleHeader(rule, lineStart, lineEnd);
            if (header != null) {
                return header;
            }
            lineStart = skipLineBreak(rule, lineEnd);
        }
        return null;
    }

    private static RuleHeader parseRuleHeader(String rule, int lineStart, int lineEnd) {
        int contentStart = skipInlineWhitespace(rule, lineStart, lineEnd);
        if (!matchesKeyword(rule, contentStart, lineEnd)) {
            return null;
        }

        int afterKeyword = contentStart + "RULE".length();
        if (afterKeyword >= lineEnd || !Character.isWhitespace(rule.charAt(afterKeyword))) {
            return null;
        }

        int nameStart = skipInlineWhitespace(rule, afterKeyword, lineEnd);
        if (nameStart >= lineEnd) {
            return null;
        }

        int nameEnd = trimInlineWhitespace(rule, nameStart, lineEnd);
        String prefix = rule.substring(lineStart, nameStart);
        String name = rule.substring(nameStart, nameEnd);
        return new RuleHeader(lineStart, lineEnd, prefix, name);
    }

    private static boolean matchesKeyword(String rule, int start, int lineEnd) {
        int keywordEnd = start + "RULE".length();
        return keywordEnd <= lineEnd && rule.regionMatches(start, "RULE", 0, "RULE".length());
    }

    private static int skipInlineWhitespace(String rule, int start, int end) {
        int cursor = start;
        while (cursor < end && Character.isWhitespace(rule.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int trimInlineWhitespace(String rule, int start, int end) {
        int cursor = end;
        while (cursor > start && Character.isWhitespace(rule.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }

    private static int findLineEnd(String rule, int lineStart) {
        int cursor = lineStart;
        while (cursor < rule.length()) {
            char current = rule.charAt(cursor);
            if (current == '\n' || current == '\r') {
                return cursor;
            }
            cursor++;
        }
        return rule.length();
    }

    private static int skipLineBreak(String rule, int lineEnd) {
        if (lineEnd >= rule.length()) {
            return rule.length();
        }
        if (rule.charAt(lineEnd) == '\r' && lineEnd + 1 < rule.length() && rule.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        return lineEnd + 1;
    }

    private record DomainLogAdapter(PluginLogPort log) implements LogPort {
        @Override
        public void info(String message) {
            log.info(message);
        }

        @Override
        public void warn(String message) {
            log.warn(message);
        }

        @Override
        public void debug(String message) {
            log.debug(message);
        }
    }

    private static final class ScanProfileCollector implements ScanProfileSinkPort {

        private final boolean enabled;
        private final EnumMap<ScanPhase, Duration> durations = new EnumMap<>(ScanPhase.class);
        private ScanProfile scannerProfile = ScanProfile.empty();

        private ScanProfileCollector(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void publish(ScanProfile profile) {
            if (!enabled) {
                return;
            }
            scannerProfile = scannerProfile.plus(profile);
        }

        private RuleRenderPort wrap(RuleRenderPort delegate) {
            if (!enabled) {
                return delegate;
            }
            return new ProfilingRuleRenderPort(delegate, this);
        }

        private <T> T measure(ScanPhase phase, Supplier<T> supplier) {
            if (!enabled) {
                return supplier.get();
            }
            long startedAt = System.nanoTime();
            try {
                return supplier.get();
            } finally {
                durations.merge(phase, Duration.ofNanos(System.nanoTime() - startedAt), Duration::plus);
            }
        }

        private ScanProfile profile() {
            return scannerProfile.plus(new ScanProfile(
                    durations,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0));
        }
    }

    private record ProfilingRuleRenderPort(RuleRenderPort delegate,
                                           ScanProfileCollector profileCollector) implements RuleRenderPort {
        @Override
        public String render(Rule rule) {
            return profileCollector.measure(ScanPhase.RULE_RENDERING, () -> delegate.render(rule));
        }
    }

    private record RuleHeader(int startIndex, int endIndex, String prefix, String name) {
    }

    private record RunOutput(List<Rule> domainRules,
                             List<String> renderedRules,
                             AnalysisContext context,
                             ConditionValidationReport validationReport) {
    }
}
