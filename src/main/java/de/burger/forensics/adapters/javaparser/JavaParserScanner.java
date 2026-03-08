package de.burger.forensics.adapters.javaparser;

import de.burger.forensics.adaptersupport.javaparser.DefaultConditionRenderingStrategy;
import de.burger.forensics.adaptersupport.javaparser.InstanceFieldNormalizer;
import de.burger.forensics.adaptersupport.javaparser.MethodEventExtractor;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.port.out.CodeScanPort;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * JavaParser-backed implementation of {@link CodeScanPort}.
 */
public final class JavaParserScanner implements CodeScanPort {

    private final MethodEventExtractor methodEventExtractor;
    private final JavaParserFactory parserFactory;
    private final JavaSourceFileCollector sourceFileCollector;
    private final JavaParallelFileScanExecutor parallelFileScanExecutor;

    public JavaParserScanner() {
        this(
            new MethodEventExtractor(new DefaultConditionRenderingStrategy(new InstanceFieldNormalizer())),
            new JavaParserFactory(),
            new JavaSourceFileCollector(),
            new JavaParallelFileScanExecutor()
        );
    }

    JavaParserScanner(
        MethodEventExtractor methodEventExtractor,
        JavaParserFactory parserFactory,
        JavaSourceFileCollector sourceFileCollector,
        JavaParallelFileScanExecutor parallelFileScanExecutor
    ) {
        this.methodEventExtractor = methodEventExtractor;
        this.parserFactory = parserFactory;
        this.sourceFileCollector = sourceFileCollector;
        this.parallelFileScanExecutor = parallelFileScanExecutor;
    }

    @Override
    public Stream<ScanEvent> scan(Path root) {
        return parallelFileScanExecutor
            .scan(root, sourceFileCollector.collect(root), parserFactory, methodEventExtractor)
            .stream();
    }
}
