package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.cache.CachedScanResult;
import de.burger.forensics.domain.model.cache.ScanDependency;
import de.burger.forensics.domain.model.cache.ScanPhase;
import de.burger.forensics.domain.model.cache.ScanProfile;
import de.burger.forensics.domain.model.cache.SourceFileSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses one Java source file into cacheable scan events and dependency descriptors.
 */
public final class JavaParserCachedScanResultCollector {

    private final MethodEventExtractor methodEventExtractor;
    private final JavaParserDependencyExtractor dependencyExtractor;

    public JavaParserCachedScanResultCollector(MethodEventExtractor methodEventExtractor,
                                               JavaParserDependencyExtractor dependencyExtractor) {
        this.methodEventExtractor = Objects.requireNonNull(methodEventExtractor, "Method event extractor must not be null.");
        this.dependencyExtractor = Objects.requireNonNull(dependencyExtractor, "Dependency extractor must not be null.");
    }

    public CachedScanResult collect(JavaParser parser, SourceFileSnapshot source, boolean strictParsing) {
        Objects.requireNonNull(parser, "JavaParser must not be null.");
        Objects.requireNonNull(source, "Source snapshot must not be null.");

        ScanProfileBuilder profile = new ScanProfileBuilder();
        try {
            ParseResult<CompilationUnit> parseResult = profile.measure(ScanPhase.JAVA_PARSER_PARSE,
                    () -> parser.parse(source.sourcePath()));
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                return parseFailure(source, strictParsing, profile, problemMessage(parseResult));
            }

            CompilationUnit compilationUnit = parseResult.getResult().orElseThrow();
            String packageName = profile.measure(ScanPhase.PACKAGE_EXTRACTION, () -> packageName(compilationUnit));
            List<MethodDeclaration> methods = profile.measure(ScanPhase.METHOD_DISCOVERY,
                    () -> compilationUnit.findAll(MethodDeclaration.class));
            List<ScanDependency> dependencies = profile.measure(ScanPhase.DEPENDENCY_EXTRACTION,
                    () -> dependencyExtractor.extract(compilationUnit, source.relativePath()));
            List<ScanEvent> events = profile.measure(ScanPhase.EVENT_EXTRACTION,
                    () -> collectEvents(methods, packageName));

            ScanProfile scanProfile = profile.successful(methods.size(), events.size(), dependencies.size());
            return new CachedScanResult(markParseSucceeded(source), events, dependencies, scanProfile);
        } catch (IOException | RuntimeException exception) {
            return parseFailure(source, strictParsing, profile, failureMessage(exception), exception);
        }
    }

    private List<ScanEvent> collectEvents(List<MethodDeclaration> methods, String packageName) {
        return methods.stream()
                .map(method -> methodEventExtractor.collectMethodEvents(method, packageName))
                .flatMap(List::stream)
                .toList();
    }

    private String packageName(CompilationUnit compilationUnit) {
        return compilationUnit.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
    }

    private CachedScanResult parseFailure(SourceFileSnapshot source,
                                          boolean strictParsing,
                                          ScanProfileBuilder profile,
                                          String failureMessage) {
        return parseFailure(source, strictParsing, profile, failureMessage, null);
    }

    private CachedScanResult parseFailure(SourceFileSnapshot source,
                                          boolean strictParsing,
                                          ScanProfileBuilder profile,
                                          String failureMessage,
                                          Exception cause) {
        if (strictParsing) {
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to parse Java source " + source.sourcePath() + ": " + failureMessage, cause);
        }
        ScanProfile scanProfile = profile.failed();
        return new CachedScanResult(markParseFailed(source, failureMessage), List.of(), List.of(), scanProfile);
    }

    private SourceFileSnapshot markParseSucceeded(SourceFileSnapshot source) {
        return sourceWithParseState(source, true, Optional.empty());
    }

    private SourceFileSnapshot markParseFailed(SourceFileSnapshot source, String failureMessage) {
        return sourceWithParseState(source, false, Optional.of(failureMessage));
    }

    private SourceFileSnapshot sourceWithParseState(SourceFileSnapshot source,
                                                   boolean parseSucceeded,
                                                   Optional<String> failureMessage) {
        return new SourceFileSnapshot(
                source.rootPath(),
                source.relativePath(),
                source.sourcePath(),
                source.fingerprint(),
                source.size(),
                source.lastModifiedAt(),
                parseSucceeded,
                failureMessage);
    }

    private String problemMessage(ParseResult<CompilationUnit> parseResult) {
        return parseResult.getProblems().stream()
                .findFirst()
                .map(Problem::getMessage)
                .orElse("JavaParser did not return a compilation unit.");
    }

    private String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static final class ScanProfileBuilder {

        private final java.util.EnumMap<ScanPhase, java.time.Duration> durations =
                new java.util.EnumMap<>(ScanPhase.class);

        private <T> T measure(ScanPhase phase, ThrowingSupplier<T> supplier) throws IOException {
            long startedAt = System.nanoTime();
            try {
                return supplier.get();
            } finally {
                durations.merge(phase, java.time.Duration.ofNanos(System.nanoTime() - startedAt), java.time.Duration::plus);
            }
        }

        private ScanProfile successful(int totalMethods, int totalEvents, int totalDependencies) {
            return new ScanProfile(
                    durations,
                    1,
                    1,
                    0,
                    1,
                    0,
                    totalMethods,
                    totalEvents,
                    totalDependencies);
        }

        private ScanProfile failed() {
            return new ScanProfile(
                    durations,
                    1,
                    0,
                    0,
                    1,
                    1,
                    0,
                    0,
                    0);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws IOException;
    }
}
