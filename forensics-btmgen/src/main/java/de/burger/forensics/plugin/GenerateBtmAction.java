package de.burger.forensics.plugin;

import de.burger.forensics.plugin.engine.SourceFileGuards;
import de.burger.forensics.plugin.io.ShardedWriter;
import de.burger.forensics.plugin.scan.ScanEvent;
import de.burger.forensics.plugin.scan.ScannerFacade;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracted action logic for GenerateBtmTask to keep @TaskAction lean.
 */
final class GenerateBtmAction {

    private GenerateBtmAction() {}

    static void run(GenerateBtmTask task) {
        File out = task.ensureOutputDir();
        String helper = task.getHelperFqn().get();
        String legacyPrefix = opt(task.getPackagePrefix().getOrNull());

        List<String> allPkgPrefixes = new ArrayList<>();
        if (task.getPkgPrefixes().getOrNull() != null) {
            for (String p : task.getPkgPrefixes().get()) if (!p.isBlank()) allPkgPrefixes.add(p);
        }
        if (legacyPrefix != null) allPkgPrefixes.add(legacyPrefix);

        boolean includeEntryExit = task.getEntryExit().getOrElse(true);
        long limit = task.getMaxFileBytes().getOrElse(2_000_000L);
        int minBranches = task.getMinBranchesPerMethod().getOrElse(0);
        List<String> tracked = task.resolveTrackedVars();

        int shardCount = Math.max(task.getShards().getOrElse(Runtime.getRuntime().availableProcessors()), 1);
        boolean gzip = task.getGzipOutput().getOrElse(false);
        String prefix = orDefault(task.getFilePrefix().getOrElse("tracing-"), "tracing-");
        long rotateMaxBytesValue = task.getRotateMaxBytesPerFile().getOrElse(4L * 1024 * 1024);
        long rotateIntervalSecondsValue = task.getRotateIntervalSeconds().getOrElse(0L);
        int flushThresholdValue = task.getFlushThresholdBytes().getOrElse(64 * 1024);
        long flushIntervalValue = task.getFlushIntervalMillis().getOrElse(2000L);
        boolean threadSafeValue = task.getWriterThreadSafe().getOrElse(false);

        String header = task.buildHeader(helper, allPkgPrefixes, tracked, task.getIncludeTimestamp().getOrElse(false));

        ScannerFacade scanner = new ScannerFacade();
        List<ScanEvent> events = new ArrayList<>();
        List<String> excludePkgs = Collections.emptyList();

        if (task.getIncludeJava().getOrElse(false)) {
            for (File file : task.getJavaSourceFiles()) {
                if (SourceFileGuards.shouldSkipLargeFile(file, limit, task::debug)) continue;
                try {
                    events.addAll(scanner.scan(file.toPath(), allPkgPrefixes, excludePkgs));
                } catch (StackOverflowError e) {
                    task.warn("Java file StackOverflow: " + file.getAbsolutePath() + " (skipped)");
                    task.fileLog("WARN", "Java StackOverflow: " + file.getAbsolutePath());
                } catch (Throwable t) {
                    task.warn("Java scan error: " + file.getAbsolutePath() + " -> " + t.getMessage());
                    task.fileLog("WARN", "Java scan error: " + file.getAbsolutePath() + " -> " + t.getMessage());
                }
            }
        }

        List<String> rules = new ArrayList<>();
        if (!events.isEmpty()) {
            List<ScanEvent> filtered = events.stream()
                    .filter(e -> "java".equals(e.language()))
                    .sorted(Comparator.comparing(ScanEvent::fqcn)
                            .thenComparing(ScanEvent::method)
                            .thenComparingInt(ScanEvent::line)
                            .thenComparing(ScanEvent::kind))
                    .toList();

            Set<String> seenMethods = new LinkedHashSet<>();

            for (ScanEvent e : filtered) {
                if (e.line() < 0) continue;
                if (!allPkgPrefixes.isEmpty() && allPkgPrefixes.stream().noneMatch(p -> e.fqcn().startsWith(p)))
                    continue;

                String methodKey = e.language() + ":" + e.fqcn() + ":" + e.method() + ":" + e.signature();

                if (includeEntryExit && seenMethods.add(methodKey)) {
                    rules.add(task.buildEntryRule(helper, e.fqcn(), e.method()));
                    rules.add(task.buildExitRule(helper, e.fqcn(), e.method()));
                }
                rules.addAll(task.toRules(e, helper));
            }
        }

        // Regex fallback to add Java entry/exit for missing methods
        if (task.getIncludeJava().getOrElse(false)) {
            de.burger.forensics.plugin.engine.JavaRegexParser regex = new de.burger.forensics.plugin.engine.JavaRegexParser();
            Set<String> seenJavaMethods = rules.stream()
                    .map(task::extractEntryExitMethod)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (File file : task.getJavaSourceFiles()) {
                if (SourceFileGuards.shouldSkipLargeFile(file, limit, task::debug)) continue;
                String text = read(file);
                Set<String> missing = task.findMissingJavaMethods(text, seenJavaMethods);
                if (missing.isEmpty()) continue;
                try {
                    List<String> fileRules = regex.scan(text, helper, legacyPrefix, includeEntryExit, task.getMaxStringLength().getOrElse(0));
                    for (String rule : fileRules) {
                        String mk = task.extractMethodKey(rule);
                        if (mk == null) mk = task.extractEntryExitMethod(rule);
                        if (mk == null || missing.contains(mk)) {
                            rules.add(rule);
                        }
                    }
                } catch (StackOverflowError e) {
                    task.getLogger().error("Regex fallback StackOverflow in file: " + file.getAbsolutePath()
                            + ". Skipping this file.", e);
                    task.fileLog("ERROR", "Regex fallback StackOverflow: " + file.getAbsolutePath());
                }
            }
        }

        try (ShardedWriter writer = new ShardedWriter(
                out, shardCount, gzip, prefix,
                rotateMaxBytesValue, rotateIntervalSecondsValue,
                flushThresholdValue, flushIntervalValue, threadSafeValue)) {
            writer.writeHeader(header);
            task.dispatchRules(rules, allPkgPrefixes, minBranches, shardCount, writer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate AST rules", e);
        }
    }

    private static String read(File f) {
        try { return java.nio.file.Files.readString(f.toPath()); } catch (Exception e) { return ""; }
    }

    private static String opt(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static String orDefault(String s, String def) { return (s == null || s.isBlank()) ? def : s; }
}
