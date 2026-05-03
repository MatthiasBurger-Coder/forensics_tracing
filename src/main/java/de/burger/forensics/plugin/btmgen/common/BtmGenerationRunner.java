package de.burger.forensics.plugin.btmgen.common;

import de.burger.forensics.adapters.javaparser.CachedJavaParserScanner;
import de.burger.forensics.adapters.javaparser.JavaParserScanner;
import de.burger.forensics.adapters.persistence.h2.H2ScanCacheAdapter;
import de.burger.forensics.adaptersupport.javaparser.DefaultSourceFingerprintPort;
import de.burger.forensics.application.service.ConditionValidationException;
import de.burger.forensics.application.service.GenerateRulesUseCase;
import de.burger.forensics.application.service.GenerationRequest;
import de.burger.forensics.application.service.RuleGenerationResult;
import de.burger.forensics.domain.model.Rule;
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
import de.burger.forensics.plugin.btmgen.render.BytemanRuleRenderer;
import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistries;
import de.burger.forensics.plugin.btmgen.render.spi.StrategyRegistry;
import de.burger.forensics.plugin.btmgen.writer.BtmFileWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
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

        createOutputDirectory(outputFile);

        RunOutput output = request.templateRequest()
                .map(template -> new RunOutput(
                        renderTemplate(renderer, request, template, profileCollector),
                        ConditionValidationReport.empty()))
                .orElseGet(() -> scanSources(renderer, request, profileCollector));

        List<String> uniqueRules = new ArrayList<>(new LinkedHashSet<>(output.rules()));
        List<String> dedupedRuleNames = dedupeRuleHeaders(uniqueRules);
        profileCollector.measure(ScanPhase.BTM_FILE_WRITING, () -> {
            writeRules(request, dedupedRuleNames);
            return null;
        });

        ScanProfile profile = profileCollector.profile();
        publishProfile(request, profile);
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
        List<String> allRules = new ArrayList<>();
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
            allRules.addAll(result.renderedRules());
            validationReport = validationReport.merge(result.validationReport());
        }
        return new RunOutput(allRules, validationReport);
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

    private void publishProfile(BtmGenerationRequest request, ScanProfile profile) {
        if (!request.profilingEnabled()) {
            return;
        }
        new JsonScanProfileSinkAdapter(request.profileReportFile()).publish(profile);
    }

    private String helperFqn(BtmGenerationRequest request) {
        String helper = request.helperFqn();
        return helper.isBlank() ? BtmGenerationDefaults.DEFAULT_HELPER_FQN : helper;
    }

    private void writeRules(BtmGenerationRequest request, List<String> rules) {
        Path outputFile = request.outputFile();
        try {
            createWriter(outputFile, request.includeTimestampHeader()).write(rules);
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

    private record RunOutput(List<String> rules, ConditionValidationReport validationReport) {
    }
}
