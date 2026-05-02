package de.burger.forensics.adapters.javaparser;

import com.github.javaparser.JavaParser;
import de.burger.forensics.adaptersupport.javaparser.DefaultConditionRenderingStrategy;
import de.burger.forensics.adaptersupport.javaparser.InstanceFieldNormalizer;
import de.burger.forensics.adaptersupport.javaparser.JavaParserCachedScanResultCollector;
import de.burger.forensics.adaptersupport.javaparser.JavaParserDependencyExtractor;
import de.burger.forensics.adaptersupport.javaparser.MethodEventExtractor;
import de.burger.forensics.adaptersupport.javaparser.scanner.JavaParserFactory;
import de.burger.forensics.adaptersupport.javaparser.scanner.JavaSourceFileCollector;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.cache.CachedScanResult;
import de.burger.forensics.domain.model.cache.ScanPhase;
import de.burger.forensics.domain.model.cache.ScanProfile;
import de.burger.forensics.domain.model.cache.SourceFileSnapshot;
import de.burger.forensics.domain.port.out.CodeScanPort;
import de.burger.forensics.domain.port.out.ScanCachePort;
import de.burger.forensics.domain.port.out.ScanProfileSinkPort;
import de.burger.forensics.domain.port.out.SourceFingerprintPort;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * JavaParser-backed scanner that stores per-file scan results behind domain cache ports.
 */
public final class CachedJavaParserScanner implements CodeScanPort {

    private final ScanCachePort scanCache;
    private final SourceFingerprintPort sourceFingerprintPort;
    private final Optional<ScanProfileSinkPort> scanProfileSinkPort;
    private final JavaParserFactory parserFactory;
    private final JavaSourceFileCollector sourceFileCollector;
    private final JavaParserCachedScanResultCollector scanResultCollector;
    private final boolean strictParsing;

    public CachedJavaParserScanner(ScanCachePort scanCache, SourceFingerprintPort sourceFingerprintPort) {
        this(scanCache, sourceFingerprintPort, null, false);
    }

    public CachedJavaParserScanner(ScanCachePort scanCache,
                                   SourceFingerprintPort sourceFingerprintPort,
                                   ScanProfileSinkPort scanProfileSinkPort,
                                   boolean strictParsing) {
        this(
                scanCache,
                sourceFingerprintPort,
                scanProfileSinkPort,
                new JavaParserFactory(),
                new JavaSourceFileCollector(),
                new MethodEventExtractor(new DefaultConditionRenderingStrategy(new InstanceFieldNormalizer())),
                strictParsing);
    }

    public CachedJavaParserScanner(ScanCachePort scanCache,
                                   SourceFingerprintPort sourceFingerprintPort,
                                   ScanProfileSinkPort scanProfileSinkPort,
                                   JavaParserFactory parserFactory,
                                   JavaSourceFileCollector sourceFileCollector,
                                   MethodEventExtractor methodEventExtractor,
                                   boolean strictParsing) {
        this.scanCache = Objects.requireNonNull(scanCache, "Scan cache must not be null.");
        this.sourceFingerprintPort = Objects.requireNonNull(sourceFingerprintPort, "Source fingerprint port must not be null.");
        this.scanProfileSinkPort = Optional.ofNullable(scanProfileSinkPort);
        this.parserFactory = Objects.requireNonNull(parserFactory, "JavaParser factory must not be null.");
        this.sourceFileCollector = Objects.requireNonNull(sourceFileCollector, "Source file collector must not be null.");
        this.scanResultCollector = new JavaParserCachedScanResultCollector(
                Objects.requireNonNull(methodEventExtractor, "Method event extractor must not be null."),
                new JavaParserDependencyExtractor());
        this.strictParsing = strictParsing;
    }

    @Override
    public Stream<ScanEvent> scan(Path root) {
        Objects.requireNonNull(root, "Scan root must not be null.");

        ScanProfileBuilder profile = new ScanProfileBuilder();
        scanCache.initialize();
        List<Path> sourceFiles = profile.measure(ScanPhase.SOURCE_FILE_DISCOVERY, () -> sourceFileCollector.collect(root));
        Set<String> currentRelativePaths = new LinkedHashSet<>();
        List<ScanEvent> events = new ArrayList<>();
        ParserHolder parserHolder = new ParserHolder(root, parserFactory, profile);

        for (Path sourceFile : sourceFiles) {
            SourceFileSnapshot source = profile.measure(ScanPhase.FINGERPRINT_CALCULATION,
                    () -> sourceFingerprintPort.snapshot(root, sourceFile));
            currentRelativePaths.add(source.relativePath());

            Optional<CachedScanResult> cachedResult = profile.measure(ScanPhase.CACHE_READ, () -> scanCache.find(source))
                    .filter(result -> isCacheHit(source, result));
            if (cachedResult.isPresent()) {
                CachedScanResult cached = cachedResult.orElseThrow();
                profile.add(cacheHitProfile(cached));
                events.addAll(cached.events());
            } else {
                CachedScanResult result = scanResultCollector.collect(parserHolder.parser(), source, strictParsing);
                profile.add(result.profile());
                profile.measure(ScanPhase.CACHE_WRITE, () -> {
                    scanCache.store(result);
                    return null;
                });
                events.addAll(result.events());
            }
        }

        profile.measure(ScanPhase.CACHE_WRITE, () -> {
            scanCache.deleteMissing(normalizedRoot(root), currentRelativePaths);
            return null;
        });
        ScanProfile scanProfile = profile.build();
        scanProfileSinkPort.ifPresent(sink -> sink.publish(scanProfile));
        return events.stream();
    }

    private boolean isCacheHit(SourceFileSnapshot source, CachedScanResult result) {
        SourceFileSnapshot cachedSource = result.source();
        return cachedSource.rootPath().equals(source.rootPath())
                && cachedSource.relativePath().equals(source.relativePath())
                && cachedSource.fingerprint().equals(source.fingerprint());
    }

    private ScanProfile cacheHitProfile(CachedScanResult cached) {
        return new ScanProfile(
                java.util.Map.of(),
                1,
                0,
                1,
                0,
                0,
                cached.profile().totalMethods(),
                cached.events().size(),
                cached.dependencies().size());
    }

    private String normalizedRoot(Path root) {
        return root.toAbsolutePath().normalize().toString();
    }

    private static final class ParserHolder {

        private final Path root;
        private final JavaParserFactory parserFactory;
        private final ScanProfileBuilder profile;
        private JavaParser parser;

        private ParserHolder(Path root, JavaParserFactory parserFactory, ScanProfileBuilder profile) {
            this.root = root;
            this.parserFactory = parserFactory;
            this.profile = profile;
        }

        private JavaParser parser() {
            if (parser == null) {
                parser = profile.measure(ScanPhase.TYPE_SOLVER_SETUP, () -> parserFactory.create(root));
            }
            return parser;
        }
    }

    private static final class ScanProfileBuilder {

        private final EnumMap<ScanPhase, Duration> durations = new EnumMap<>(ScanPhase.class);
        private int totalFiles;
        private int parsedFiles;
        private int cacheHitFiles;
        private int cacheMissFiles;
        private int failedFiles;
        private int totalMethods;
        private int totalEvents;
        private int totalDependencies;

        private <T> T measure(ScanPhase phase, Supplier<T> supplier) {
            long startedAt = System.nanoTime();
            try {
                return supplier.get();
            } finally {
                durations.merge(phase, Duration.ofNanos(System.nanoTime() - startedAt), Duration::plus);
            }
        }

        private void add(ScanProfile profile) {
            profile.phaseDurations().forEach((phase, duration) -> durations.merge(phase, duration, Duration::plus));
            totalFiles += profile.totalFiles();
            parsedFiles += profile.parsedFiles();
            cacheHitFiles += profile.cacheHitFiles();
            cacheMissFiles += profile.cacheMissFiles();
            failedFiles += profile.failedFiles();
            totalMethods += profile.totalMethods();
            totalEvents += profile.totalEvents();
            totalDependencies += profile.totalDependencies();
        }

        private ScanProfile build() {
            return new ScanProfile(
                    durations,
                    totalFiles,
                    parsedFiles,
                    cacheHitFiles,
                    cacheMissFiles,
                    failedFiles,
                    totalMethods,
                    totalEvents,
                    totalDependencies);
        }
    }
}
