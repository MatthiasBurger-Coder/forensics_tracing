package de.burger.forensics.adapters.javaparser;

import com.github.javaparser.JavaParser;
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

    public JavaParserScanner() {
        this(
            new MethodEventExtractor(new DefaultConditionRenderingStrategy(new InstanceFieldNormalizer())),
            new JavaParserFactory(),
            new JavaSourceFileCollector()
        );
    }

    JavaParserScanner(
        MethodEventExtractor methodEventExtractor,
        JavaParserFactory parserFactory,
        JavaSourceFileCollector sourceFileCollector
    ) {
        this.methodEventExtractor = methodEventExtractor;
        this.parserFactory = parserFactory;
        this.sourceFileCollector = sourceFileCollector;
    }

    @Override
    public Stream<ScanEvent> scan(Path root) {
        JavaParser parser = parserFactory.create(root);
        return sourceFileCollector.collect(root).stream()
            .map(file -> JavaParserScanEventCollector.collectSafely(parser, file, methodEventExtractor))
            .flatMap(java.util.Collection::stream);
    }
}
