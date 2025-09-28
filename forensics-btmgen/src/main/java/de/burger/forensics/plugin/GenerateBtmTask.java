package de.burger.forensics.plugin;

import de.burger.forensics.plugin.engine.JavaPrefilter;
import de.burger.forensics.plugin.engine.JavaRegexParser;
import de.burger.forensics.plugin.engine.SourceFileGuards;
import de.burger.forensics.plugin.io.ShardedWriter;
import de.burger.forensics.plugin.scan.ScanEvent;
import de.burger.forensics.plugin.scan.ScannerFacade;
import de.burger.forensics.plugin.strategy.ConditionStrategy;
import de.burger.forensics.plugin.strategy.DefaultStrategyFactory;
import de.burger.forensics.plugin.strategy.SafeModeDecorator;
import de.burger.forensics.plugin.strategy.StrategyFactory;
import de.burger.forensics.plugin.translate.UnsafeExprTranslator;
import de.burger.forensics.plugin.util.HashUtil;
import de.burger.forensics.plugin.util.RuleIdUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Java port of GenerateBtmTask.
 * - Configuration-cache friendly (no task.project calls at execution time).
 * - Injects Gradle services and wires inputs during configuration phase.
 * - Provides conventions for all required properties (passes Gradle validation).
 * - Adds StackOverflowError guard for regex fallback (catastrophic backtracking).
 * - Uses ShardedWriter with rotation/flush/thread-safe options.
 * - English comments as requested.
 */
@CacheableTask
public abstract class GenerateBtmTask extends DefaultTask {

    // ---- Injected Gradle services (configuration-cache safe)
    @Inject public abstract ProjectLayout getLayout();
    @Inject public abstract ObjectFactory getObjects();
    @Inject public abstract ProviderFactory getProviders();

    // ---- Strategy factory (same behaviour as Kotlin version)
    private final StrategyFactory conditionStrategyFactory = new DefaultStrategyFactory();

    // ---- Constants
    private static final String SAFE_EVAL_FQCN = "org.example.trace.SafeEval";
    private static final String SUBJECTLESS_WHEN_PLACEHOLDER = "when { … }";

    private static final Pattern JAVA_PACKAGE_REGEX =
            Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern JAVA_CLASS_REGEX =
            Pattern.compile("(?m)^\\s*(?:@[\\w.$]+(?:\\([^)]*\\))?\\s*)*(?:(?:\\b(?:public|protected|private|abstract|final|static|strictfp|sealed)\\b|non-sealed)\\s+)*class\\s+([A-Za-z0-9_]+)");
    private static final Pattern JAVA_METHOD_REGEX =
            Pattern.compile("(?m)^\\s*(?:@[\\w.$]+(?:\\([^)]*\\))?\\s*)*(?:\\b(?:public|protected|private|abstract|final|static|strictfp|synchronized|native|default)\\b\\s+)*(?:<[^>]+>\\s*)?[\\w$<>\\[\\],.?\\s]+\\s+([A-Za-z0-9_]+)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern ENTRY_EXIT_RULE_REGEX =
            Pattern.compile("^RULE\\s+(?:enter|exit)@([\\w.$]+)\\.([A-Za-z0-9_]+)", Pattern.MULTILINE);

    // ---- Inputs (mirroring the Kotlin task)

    @Input public abstract ListProperty<String> getSrcDirs();
    @Input @Optional public abstract Property<String> getPackagePrefix();
    @Input public abstract Property<String> getHelperFqn();
    @Input public abstract Property<Boolean> getEntryExit();
    @Input public abstract ListProperty<String> getTrackedVars();
    @Input public abstract Property<Boolean> getIncludeJava();
    @Input public abstract Property<Boolean> getIncludeTimestamp();
    @Input public abstract Property<Integer> getMaxStringLength();
    @Input public abstract Property<Long> getMaxFileBytes();

    @Input public abstract ListProperty<String> getPkgPrefixes();
    @Input public abstract ListProperty<String> getIncludePatterns();
    @Input public abstract ListProperty<String> getExcludePatterns();
    @Input public abstract Property<Integer> getParallelism();
    @Input public abstract Property<Integer> getShards();
    @Input public abstract Property<Boolean> getGzipOutput();
    @Input public abstract Property<String> getFilePrefix();
    @Input public abstract Property<Long> getRotateMaxBytesPerFile();
    @Input public abstract Property<Long> getRotateIntervalSeconds();
    @Input public abstract Property<Integer> getFlushThresholdBytes();
    @Input public abstract Property<Long> getFlushIntervalMillis();
    @Input public abstract Property<Boolean> getWriterThreadSafe();
    @Input @Optional public abstract Property<String> getLogLevel();
    @Input @Optional public abstract Property<Boolean> getLogToFile();
    @Input @Optional public abstract Property<String> getLogFilePath();
    @Input public abstract Property<Integer> getMinBranchesPerMethod();
    @Input public abstract Property<Boolean> getSafeMode();
    @Input public abstract Property<Boolean> getForceHelperForWhitelist();
    @Input public abstract Property<Boolean> getUseAstScanner();

    @OutputDirectory public abstract DirectoryProperty getOutputDir();

    // File inputs (explicitly wired from plugin during configuration)
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    // ---- Conventions & defaults
    @Inject
    public GenerateBtmTask() {
        // Provide safe conventions to satisfy Gradle validation.
        getSrcDirs().convention(Arrays.asList("src/main/java", "src/main/kotlin"));
        getPackagePrefix().convention("");
        getHelperFqn().convention("de.burger.forensics.ForensicsHelper");
        getEntryExit().convention(true);
        getTrackedVars().convention(Collections.emptyList());
        getIncludeJava().convention(true);
        getIncludeTimestamp().convention(true);
        getMaxStringLength().convention(0);
        getMaxFileBytes().convention(2_000_000L);

        getPkgPrefixes().convention(Collections.emptyList());
        getIncludePatterns().convention(Arrays.asList("**/*.java", "**/*.kt"));
        getExcludePatterns().convention(Arrays.asList("**/build/**", "**/.gradle/**", "**/out/**", "**/generated/**"));
        int cpu = Math.max(Runtime.getRuntime().availableProcessors(), 1);
        getParallelism().convention(cpu);
        getShards().convention(cpu);
        getGzipOutput().convention(false);
        getFilePrefix().convention("tracing-");
        getRotateMaxBytesPerFile().convention(10L * 1024 * 1024);
        getRotateIntervalSeconds().convention(300L);
        getFlushThresholdBytes().convention(64 * 1024);
        getFlushIntervalMillis().convention(1_000L);
        getWriterThreadSafe().convention(true);

        getLogLevel().convention("INFO");
        getLogToFile().convention(true);
        getLogFilePath().convention("logs/forensics-btmgen.log");

        getMinBranchesPerMethod().convention(0);
        getSafeMode().convention(true);
        getForceHelperForWhitelist().convention(false);
        getUseAstScanner().convention(true);

        // Configuration-cache safe: resolve output via injected layout
        getOutputDir().convention(getLayout().getBuildDirectory().dir("forensics"));
    }

    // ---- Task action
    @TaskAction
    public void generate() {
        if (getUseAstScanner().getOrElse(true)) {
            generateWithAst();
        } else {
            generateLegacy();
        }
    }

    // -------------------------
    // Legacy path (regex)
    // -------------------------
    private void generateLegacy() {
        File out = ensureOutputDir();
        String helper = getHelperFqn().get();
        String legacyPrefix = opt(getPackagePrefix().getOrNull());

        List<String> allPkgPrefixes = new ArrayList<>();
        if (getPkgPrefixes().getOrNull() != null) {
            for (String p : getPkgPrefixes().get()) if (!p.isBlank()) allPkgPrefixes.add(p);
        }
        if (legacyPrefix != null) allPkgPrefixes.add(legacyPrefix);

        boolean includeEntryExit = getEntryExit().getOrElse(true);
        int maxLen = getMaxStringLength().getOrElse(0);
        long limit = getMaxFileBytes().getOrElse(2_000_000L);
        List<String> tracked = resolveTrackedVars();

        int shardCount = Math.max(getShards().getOrElse(Runtime.getRuntime().availableProcessors()), 1);
        boolean gzip = getGzipOutput().getOrElse(false);
        String prefix = orDefault(getFilePrefix().getOrElse("tracing-"), "tracing-");
        long rotateMaxBytesValue = getRotateMaxBytesPerFile().getOrElse(4L * 1024 * 1024);
        long rotateIntervalSecondsValue = getRotateIntervalSeconds().getOrElse(0L);
        int flushThresholdValue = getFlushThresholdBytes().getOrElse(64 * 1024);
        long flushIntervalValue = getFlushIntervalMillis().getOrElse(2000L);
        boolean threadSafeValue = getWriterThreadSafe().getOrElse(false);
        int minBranches = getMinBranchesPerMethod().getOrElse(0);

        String header = buildHeader(helper, allPkgPrefixes, tracked, getIncludeTimestamp().getOrElse(false));

        try (ShardedWriter writer = new ShardedWriter(
                out, shardCount, gzip, prefix,
                rotateMaxBytesValue, rotateIntervalSecondsValue,
                flushThresholdValue, flushIntervalValue, threadSafeValue)) {

            writer.writeHeader(header);

            if (getIncludeJava().getOrElse(false)) {
                JavaRegexParser scanner = new JavaRegexParser();
                for (File file : getJavaSourceFiles()) {
                    if (SourceFileGuards.shouldSkipLargeFile(file, limit, this::debug)) continue;
                    String text = read(file);
                    try {
                        List<String> fileRules = scanner.scan(text, helper, legacyPrefix, includeEntryExit, maxLen);
                        dispatchRules(fileRules, allPkgPrefixes, minBranches, shardCount, writer);
                    } catch (StackOverflowError e) {
                        warn("Regex fallback StackOverflow in file: " + file.getAbsolutePath() + " (skipped)");
                        fileLog("WARN", "Regex fallback StackOverflow: " + file.getAbsolutePath());
                    } catch (Throwable t) {
                        warn("Regex fallback error in file: " + file.getAbsolutePath() + " -> " + t.getMessage());
                        fileLog("WARN", "Regex fallback error: " + file.getAbsolutePath() + " -> " + t.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate legacy rules", e);
        }
    }

    // -------------------------
    // AST path (ScannerFacade)
    // -------------------------
    private void generateWithAst() {
        File out = ensureOutputDir();
        String helper = getHelperFqn().get();
        String legacyPrefix = opt(getPackagePrefix().getOrNull());

        List<String> allPkgPrefixes = new ArrayList<>();
        if (getPkgPrefixes().getOrNull() != null) {
            for (String p : getPkgPrefixes().get()) if (!p.isBlank()) allPkgPrefixes.add(p);
        }
        if (legacyPrefix != null) allPkgPrefixes.add(legacyPrefix);

        boolean includeEntryExit = getEntryExit().getOrElse(true);
        long limit = getMaxFileBytes().getOrElse(2_000_000L);
        int minBranches = getMinBranchesPerMethod().getOrElse(0);
        List<String> tracked = resolveTrackedVars();

        int shardCount = Math.max(getShards().getOrElse(Runtime.getRuntime().availableProcessors()), 1);
        boolean gzip = getGzipOutput().getOrElse(false);
        String prefix = orDefault(getFilePrefix().getOrElse("tracing-"), "tracing-");
        long rotateMaxBytesValue = getRotateMaxBytesPerFile().getOrElse(4L * 1024 * 1024);
        long rotateIntervalSecondsValue = getRotateIntervalSeconds().getOrElse(0L);
        int flushThresholdValue = getFlushThresholdBytes().getOrElse(64 * 1024);
        long flushIntervalValue = getFlushIntervalMillis().getOrElse(2000L);
        boolean threadSafeValue = getWriterThreadSafe().getOrElse(false);

        String header = buildHeader(helper, allPkgPrefixes, tracked, getIncludeTimestamp().getOrElse(false));

        ScannerFacade scanner = new ScannerFacade();
        List<ScanEvent> events = new ArrayList<>();
        List<String> includePkgs = allPkgPrefixes;
        List<String> excludePkgs = Collections.emptyList();

        // Kotlin first (if present)
        for (File file : getKotlinSourceFiles()) {
            if (SourceFileGuards.shouldSkipLargeFile(file, limit, this::debug)) continue;
            try {
                events.addAll(scanner.scan(file.toPath(), includePkgs, excludePkgs));
            } catch (StackOverflowError e) {
                warn("Kotlin file StackOverflow: " + file.getAbsolutePath() + " (skipped)");
                fileLog("WARN", "Kotlin StackOverflow: " + file.getAbsolutePath());
            } catch (Throwable t) {
                warn("Kotlin scan error: " + file.getAbsolutePath() + " -> " + t.getMessage());
                fileLog("WARN", "Kotlin scan error: " + file.getAbsolutePath() + " -> " + t.getMessage());
            }
        }

        if (getIncludeJava().getOrElse(false)) {
            for (File file : getJavaSourceFiles()) {
                if (SourceFileGuards.shouldSkipLargeFile(file, limit, this::debug)) continue;
                try {
                    events.addAll(scanner.scan(file.toPath(), includePkgs, excludePkgs));
                } catch (StackOverflowError e) {
                    warn("Java file StackOverflow: " + file.getAbsolutePath() + " (skipped)");
                    fileLog("WARN", "Java StackOverflow: " + file.getAbsolutePath());
                } catch (Throwable t) {
                    warn("Java scan error: " + file.getAbsolutePath() + " -> " + t.getMessage());
                    fileLog("WARN", "Java scan error: " + file.getAbsolutePath() + " -> " + t.getMessage());
                }
            }
        }

        List<String> rules = new ArrayList<>();
        if (!events.isEmpty()) {
            List<ScanEvent> filtered = events.stream()
                    .sorted(Comparator.comparing(ScanEvent::language)
                            .thenComparing(ScanEvent::fqcn)
                            .thenComparing(ScanEvent::method)
                            .thenComparingInt(ScanEvent::line)
                            .thenComparing(ScanEvent::kind))
                    .collect(Collectors.toList());

            Set<String> seenMethods = new LinkedHashSet<>();
            Set<String> methodsWithKotlinSwitch = filtered.stream()
                    .filter(e -> "kotlin".equals(e.language()) && "switch".equals(e.kind()))
                    .map(e -> e.language() + ":" + e.fqcn() + ":" + e.method() + ":" + e.signature())
                    .collect(Collectors.toSet());

            for (ScanEvent e : filtered) {
                if (e.line() < 0) continue;
                if (!allPkgPrefixes.isEmpty() && allPkgPrefixes.stream().noneMatch(p -> e.fqcn().startsWith(p)))
                    continue;

                String methodKey = e.language() + ":" + e.fqcn() + ":" + e.method() + ":" + e.signature();

                if (includeEntryExit && seenMethods.add(methodKey)) {
                    rules.add(buildEntryRule(helper, e.fqcn(), e.method()));
                    rules.add(buildExitRule(helper, e.fqcn(), e.method()));
                }
                if ("kotlin".equals(e.language()) && "when-branch".equals(e.kind())
                        && !methodsWithKotlinSwitch.contains(methodKey)) {
                    ScanEvent synthetic = new ScanEvent("kotlin", e.fqcn(), e.method(),
                            e.signature(), "switch", e.line(), null);
                    rules.add(buildKotlinSwitchRule(synthetic, helper));
                }
                rules.addAll(toRules(e, helper));
            }
        }

        // Regex fallback to add Java entry/exit for missing methods
        if (getIncludeJava().getOrElse(false)) {
            JavaRegexParser regex = new JavaRegexParser();
            Set<String> seenJavaMethods = rules.stream()
                    .map(this::extractEntryExitMethod)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (File file : getJavaSourceFiles()) {
                if (SourceFileGuards.shouldSkipLargeFile(file, limit, this::debug)) continue;
                String text = read(file);
                Set<String> missing = findMissingJavaMethods(text, seenJavaMethods);
                if (missing.isEmpty()) continue;
                try {
                    List<String> fileRules = regex.scan(text, helper, legacyPrefix, includeEntryExit, getMaxStringLength().getOrElse(0));
                    for (String rule : fileRules) {
                        String mk = extractMethodKey(rule);
                        if (mk == null) mk = extractEntryExitMethod(rule);
                        if (mk == null || missing.contains(mk)) {
                            rules.add(rule);
                        }
                    }
                } catch (StackOverflowError e) {
                    getLogger().error("Regex fallback StackOverflow in file: " + file.getAbsolutePath()
                            + ". Skipping this file.", e);
                    fileLog("ERROR", "Regex fallback StackOverflow: " + file.getAbsolutePath());
                }
            }
        }

        try (ShardedWriter writer = new ShardedWriter(
                out, shardCount, gzip, prefix,
                rotateMaxBytesValue, rotateIntervalSecondsValue,
                flushThresholdValue, flushIntervalValue, threadSafeValue)) {
            writer.writeHeader(header);
            dispatchRules(rules, allPkgPrefixes, minBranches, shardCount, writer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate AST rules", e);
        }
    }

    // -------------------------
    // Rules & utilities
    // -------------------------

    private List<String> toRules(ScanEvent event, String helper) {
        switch (event.language()) {
            case "java":
                switch (event.kind()) {
                    case "if-true": return Collections.singletonList(buildJavaIfRule(event, helper, true));
                    case "if-false": return Collections.singletonList(buildJavaIfRule(event, helper, false));
                    case "switch":   return Collections.singletonList(buildJavaSwitchRule(event, helper));
                    case "switch-case": return Collections.singletonList(buildJavaCaseRule(event, helper));
                    default: return Collections.emptyList();
                }
            case "kotlin":
                switch (event.kind()) {
                    case "if-true": return Collections.singletonList(buildKotlinIfRule(event, helper, true));
                    case "if-false": return Collections.singletonList(buildKotlinIfRule(event, helper, false));
                    case "switch":   return Collections.singletonList(buildKotlinSwitchRule(event, helper));
                    case "when-branch": return Collections.singletonList(buildKotlinCaseRule(event, helper));
                    case "write":    return Collections.singletonList(buildKotlinWriteRule(event, helper));
                    default: return Collections.emptyList();
                }
            default: return Collections.emptyList();
        }
    }

    private String buildJavaIfRule(ScanEvent e, String helper, boolean positive) {
        String conditionText = e.conditionText() != null ? e.conditionText() : "true";
        String check = positive ? ("IF (" + conditionText + ")") : ("IF (!(" + conditionText + "))");
        return String.join("\n",
            "RULE " + e.fqcn() + "." + e.method() + ":" + e.line() + ":" + (positive ? "if-true" : "if-false"),
            "CLASS " + e.fqcn(),
            "METHOD " + e.method() + "(..)",
            "HELPER " + helper,
            "AT LINE " + e.line(),
            check,
            "DO iff(\"" + e.fqcn() + "\",\"" + e.method() + "\"," + e.line() + ",\"" + escape(conditionText) + "\"," + positive + ")",
            "ENDRULE");
    }

    private String buildJavaSwitchRule(ScanEvent e, String helper) {
        String selector = escape(optStr(e.conditionText()));
        return String.join("\n",
            "RULE " + e.fqcn() + "." + e.method() + ":" + e.line() + ":when",
            "CLASS " + e.fqcn(),
            "METHOD " + e.method() + "(..)",
            "HELPER " + helper,
            "AT LINE " + e.line(),
            "DO sw(\"" + e.fqcn() + "\",\"" + e.method() + "\"," + e.line() + ",\"" + selector + "\")",
            "ENDRULE");
    }

    private String buildJavaCaseRule(ScanEvent e, String helper) {
        String label = escape(optStr(e.conditionText(), "default"));
        return String.join("\n",
            "RULE " + e.fqcn() + "." + e.method() + ":" + e.line() + ":case",
            "CLASS " + e.fqcn(),
            "METHOD " + e.method() + "(..)",
            "HELPER " + helper,
            "AT LINE " + e.line(),
            "DO kase(\"" + e.fqcn() + "\",\"" + e.method() + "\"," + e.line() + ",\"" + label + "\")",
            "ENDRULE");
    }

    private String buildKotlinIfRule(ScanEvent e, String helper, boolean positive) {
        String cond = e.conditionText() != null ? e.conditionText() : "true";
        ConditionStrategy base = conditionStrategyFactory.from(cond);
        String ruleId = RuleIdUtil.stableRuleId(e.fqcn(), e.method(), e.line(), cond);
        ConditionStrategy decorated = decorateCondition(base, ruleId);
        String rendered = decorated.toBytemanIf();
        List<String> reg = maybeBuildRegistrationBlock(ruleId, cond, rendered);
        String escaped = escape(cond);

        List<String> lines = new ArrayList<>();
        lines.add("RULE " + e.fqcn() + "." + e.method() + ":" + e.line() + ":" + (positive ? "if-true" : "if-false"));
        lines.add("CLASS " + e.fqcn());
        lines.add("METHOD " + e.method() + "(..)"
        );
        lines.add("HELPER " + helper);
        lines.add("AT LINE " + e.line());
        lines.add(positive ? "IF (" + rendered + ")" : "IF (!(" + rendered + "))");
        if (reg != null) lines.addAll(reg);
        lines.add("DO iff(\"" + e.fqcn() + "\",\"" + e.method() + "\"," + e.line() + ",\"" + escaped + "\"," + positive + ")");
        lines.add("ENDRULE");
        return String.join("\n", lines);
    }

    private String buildKotlinSwitchRule(ScanEvent e, String helper) {
        String raw = (e.conditionText() != null && !e.conditionText().isBlank())
                ? e.conditionText() : SUBJECTLESS_WHEN_PLACEHOLDER;
        String sel = escape(raw);
        return String.join("\n",
            "RULE " + e.fqcn() + "." + e.method() + ":" + e.line() + ":when",
            "CLASS " + e.fqcn(),
            "METHOD " + e.method() + "(..)",
            "HELPER " + helper,
            "AT LINE " + e.line(),
            "DO sw(\"" + e.fqcn() + "\",\"" + e.method() + "\"," + e.line() + ",\"" + sel + "\")",
            "ENDRULE");
    }

    private String buildKotlinCaseRule(ScanEvent e, String helper) {
        String label = escape(optStr(e.conditionText(), "else"));
        return String.join("\n",
            "RULE " + e.fqcn() + "." + e.method() + ":" + e.line() + ":case",
            "CLASS " + e.fqcn(),
            "METHOD " + e.method() + "(..)",
            "HELPER " + helper,
            "AT LINE " + e.line(),
            "DO kase(\"" + e.fqcn() + "\",\"" + e.method() + "\"," + e.line() + ",\"" + label + "\")",
            "ENDRULE");
    }

    private String buildKotlinWriteRule(ScanEvent e, String helper) {
        String name = e.conditionText();
        if (name == null || name.isBlank()) return "";
        String escapedVar = escape(name);
        return String.join("\n",
            "RULE " + e.fqcn() + "." + e.method() + ":" + e.line() + ":write-" + name,
            "CLASS " + e.fqcn(),
            "METHOD " + e.method() + "(..)",
            "HELPER " + helper,
            "AFTER WRITE $" + name,
            "DO writeVar(\"" + e.fqcn() + "\",\"" + e.method() + "\"," + e.line() + ",\"" + escapedVar + "\",$" + name + ")",
            "ENDRULE");
    }

    private void dispatchRules(List<String> rules,
                               List<String> prefixes,
                               int minBranches,
                               int shardCount,
                               ShardedWriter writer) {
        if (rules == null || rules.isEmpty()) return;

        if (minBranches <= 0) {
            for (String rule : rules) {
                if (passesPrefixFilter(rule, prefixes)) {
                    String shardKey = computeShardKey(rule);
                    int shard = HashUtil.stableShard(shardKey, shardCount);
                    appendRule(writer, shard, rule);
                }
            }
            return;
        }

        Map<String, List<String>> grouped = rules.stream()
                .collect(Collectors.groupingBy(this::extractMethodKey, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
            String methodKey = e.getKey();
            List<String> methodRules = e.getValue();
            if (methodKey == null || hasRequiredBranches(methodRules, minBranches)) {
                String first = methodRules.isEmpty() ? null : methodRules.get(0);
                if (first == null || !passesPrefixFilter(first, prefixes)) continue;
                for (String rule : methodRules) {
                    String shardKey = computeShardKey(rule);
                    int shard = HashUtil.stableShard(shardKey, shardCount);
                    appendRule(writer, shard, rule);
                }
            }
        }
    }

    private void appendRule(ShardedWriter writer, int shard, String rule) {
        try {
            writer.append(shard, rule);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append rule", e);
        }
    }

    private boolean passesPrefixFilter(String rule, List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) return true;
        String cls = extractClassName(rule);
        return (cls != null) && prefixes.stream().anyMatch(cls::startsWith);
    }

    private boolean hasRequiredBranches(List<String> rules, int minBranches) {
        if (minBranches <= 0) return true;
        long cnt = rules.stream().filter(r ->
            r.contains(":if-") || r.contains(":is-") || r.contains(":when") || r.contains(":case")
        ).count();
        return cnt >= minBranches;
    }

    private String computeShardKey(String rule) {
        String cls = optStr(extractClassName(rule), "");
        String method = findGroup("(?m)^\\s*METHOD\\s+([A-Za-z0-9_]+)\\(", rule, 1);
        if (method == null) method = "";
        String line = findGroup("(?m)^\\s*AT\\s+LINE\\s+(\\d+)", rule, 1);
        if (line == null) line = "0";
        return cls + "#" + method + ":" + line;
    }

    private String buildHeader(String helper, List<String> prefixes, List<String> tracked, boolean withTs) {
        StringBuilder sb = new StringBuilder();
        if (withTs) sb.append("# Generated at ").append(Instant.now()).append(" by de.burger.forensics.btmgen\n");
        else sb.append("# Generated by de.burger.forensics.btmgen\n");
        sb.append("# Helper: ").append(helper).append('\n');
        if (!prefixes.isEmpty()) {
            sb.append("# Package prefix filters: ").append(String.join(", ", prefixes)).append('\n');
        }
        if (tracked != null && !tracked.isEmpty()) {
            sb.append("# Tracked variables: ").append(String.join(", ", tracked)).append('\n');
        }
        return sb.toString();
    }

    private List<String> resolveTrackedVars() {
        List<String> raw = getTrackedVars().getOrElse(Collections.emptyList());
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) unique.add(trimmed);
        }
        return new ArrayList<>(unique);
    }

    // -------------------------
    // File resolution & helpers
    // -------------------------

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    protected List<File> getKotlinSourceFiles() {
        return resolveFiles(".kt");
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    protected List<File> getJavaSourceFiles() {
        return getIncludeJava().getOrElse(false) ? resolveFiles(".java") : Collections.emptyList();
    }

    private List<File> resolveFiles(String withExtension) {
        List<String> dirs = getSrcDirs().getOrNull();
        if (dirs == null) return Collections.emptyList();
        List<File> directories = dirs.stream()
                .map(this::resolvePath)
                .filter(File::exists)
                .collect(Collectors.toList());
        List<String> includes = getIncludePatterns().getOrNull();
        List<String> excludes = getExcludePatterns().getOrElse(Collections.emptyList());

        List<File> result = new ArrayList<>();
        for (File dir : directories) {
            try (var paths = Files.walk(dir.toPath())) {
                paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(withExtension))
                        .forEach(p -> {
                            String rel = dir.toPath().relativize(p).toString().replace('\\', '/');
                            boolean incOk = (includes == null || includes.isEmpty()) || includes.stream().anyMatch(glob -> globMatchesPath(rel, glob));
                            boolean excOk = excludes.stream().noneMatch(glob -> globMatchesPath(rel, glob));
                            if (incOk && excOk) result.add(p.toFile());
                        });
            } catch (Exception ignored) {}
        }
        result.sort(Comparator.comparing(File::getAbsolutePath));
        return result;
    }

    private File ensureOutputDir() {
        File outputDirectory = getOutputDir().get().getAsFile();
        if (!outputDirectory.exists()) outputDirectory.mkdirs();
        return outputDirectory;
    }

    private File resolvePath(String path) {
        File f = new File(path);
        if (f.isAbsolute()) return f;
        return getLayout().getProjectDirectory().file(path).getAsFile();
    }

    // Simplistic glob matcher supporting ** and *
    private static boolean globMatchesPath(String path, String pattern) {
        String regex = pattern.replace(".", "\\.")
                .replace("**/", "(.*/)?")
                .replace("/**", "(/.*)?")
                .replace("**", ".*")
                .replace("*", "[^/]*");
        return path.matches(regex);
    }

    private static String read(File f) {
        try { return Files.readString(f.toPath()); }
        catch (Exception e) { return ""; }
    }

    private Set<String> findMissingJavaMethods(String text, Set<String> seenJavaMethods) {
        String sanitized = JavaPrefilter.prefilterJava(text);
        String pkg = group(JAVA_PACKAGE_REGEX.matcher(sanitized), 1);
        Set<String> missing = new LinkedHashSet<>();
        int searchIndex = 0;
        while (true) {
            Matcher classMatch = JAVA_CLASS_REGEX.matcher(sanitized);
            if (!classMatch.find(searchIndex)) break;
            int openIndex = sanitized.indexOf('{', classMatch.end());
            if (openIndex < 0) { searchIndex = classMatch.end(); continue; }
            int closeIndex = findMatchingBrace(sanitized, openIndex);
            String className = classMatch.group(1);
            String fqcn = (pkg == null || pkg.isBlank()) ? className : (pkg + "." + className);
            String body = sanitized.substring(openIndex + 1, closeIndex);
            Matcher m = JAVA_METHOD_REGEX.matcher(body);
            while (m.find()) {
                String methodName = m.group(1);
                String key = fqcn + "." + methodName;
                if (!seenJavaMethods.contains(key)) missing.add(key);
            }
            searchIndex = closeIndex + 1;
        }
        return missing;
    }

    private static int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return text.length() - 1;
    }

    private @Nullable String extractClassName(String rule) {
        return group(Pattern.compile("(?m)^\\s*CLASS\\s+([\\w.$]+)").matcher(rule), 1);
    }

    private @Nullable String extractMethodKey(String rule) {
        Matcher m = Pattern.compile("(?m)^\\s*RULE\\s+([\\w.$]+)\\.([A-Za-z0-9_]+):").matcher(rule);
        if (m.find()) return m.group(1) + "." + m.group(2);
        return null;
    }

    private @Nullable String extractEntryExitMethod(String rule) {
        Matcher m = ENTRY_EXIT_RULE_REGEX.matcher(rule);
        if (m.find()) return m.group(1) + "." + m.group(2);
        return null;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private ConditionStrategy decorateCondition(ConditionStrategy base, String ruleId) {
        return new SafeModeDecorator(
                base,
                getSafeMode().getOrElse(false),
                getForceHelperForWhitelist().getOrElse(false),
                SAFE_EVAL_FQCN,
                ruleId
        );
    }

    private @Nullable List<String> maybeBuildRegistrationBlock(String ruleId, String rawExpression, String renderedCondition) {
        String expected = SAFE_EVAL_FQCN + ".ifMatch(\"" + ruleId + "\")";
        if (!expected.equals(renderedCondition)) return null;
        String body = UnsafeExprTranslator.toHelperExpr(rawExpression);
        String fqcn = SAFE_EVAL_FQCN;
        return Arrays.asList(
                "DO " + fqcn + ".register(\"" + ruleId + "\", new " + fqcn + ".Evaluator() {",
                "    public boolean eval() {",
                "        return " + body + ";",
                "    }",
                "});"
        );
    }

    private String buildEntryRule(String helper, String className, String methodName) {
        return ("" +
                "RULE enter@" + className + "." + methodName + "\n" +
                "CLASS " + className + "\n" +
                "METHOD " + methodName + "(..)\n" +
                "HELPER " + helper + "\n" +
                "AT ENTRY\n" +
                "DO enter(\"" + className + "\",\"" + methodName + "\",$LINE)\n" +
                "ENDRULE").trim();
    }

    private String buildExitRule(String helper, String className, String methodName) {
        return ("" +
                "RULE exit@" + className + "." + methodName + "\n" +
                "CLASS " + className + "\n" +
                "METHOD " + methodName + "(..)\n" +
                "HELPER " + helper + "\n" +
                "AT EXIT\n" +
                "DO exit(\"" + className + "\",\"" + methodName + "\",$LINE)\n" +
                "ENDRULE").trim();
    }

    private void debug(String msg) {
        if ("DEBUG".equalsIgnoreCase(getLogLevel().getOrElse("INFO"))) {
            getLogger().debug(msg);
            fileLog("DEBUG", msg);
        }
    }
    private void warn(String msg) {
        getLogger().warn(msg);
    }

    private @Nullable File ensureLogFile() {
        try {
            if (!getLogToFile().getOrElse(true)) return null;
            String relative = getLogFilePath().getOrElse("logs/forensics-btmgen.log");
            File baseDir = getLayout().getProjectDirectory().getAsFile();
            File f = new File(baseDir, relative);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!f.exists()) f.createNewFile();
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void fileLog(String level, String message) {
        if (!getLogToFile().getOrElse(true)) return;
        try {
            File f = ensureLogFile();
            if (f == null) return;
            String ts = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .format(java.time.LocalDateTime.now());
            Files.writeString(f.toPath(), ts + " [" + level + "] " + message + "\n", java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {}
    }

    private static @Nullable String group(Matcher m, int idx) {
        return m.find() ? m.group(idx) : null;
    }
    private static String findGroup(String regex, String text, int idx) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(idx) : null;
    }
    private static @Nullable String opt(String s) { return (s == null || s.isBlank()) ? null : s; }
    private static String optStr(@Nullable String s) { return s == null ? "" : s; }
    private static String optStr(@Nullable String s, String def) { return (s == null || s.isBlank()) ? def : s; }
    private static String orDefault(String s, String def) { return (s == null || s.isBlank()) ? def : s; }
}
