package de.burger.forensics.adaptersupport.javaparser.scanner;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import de.burger.forensics.adaptersupport.javaparser.MethodEventExtractor;
import de.burger.forensics.domain.model.ScanEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Parses a Java source file and maps it to domain scan events.
 */
public final class JavaParserScanEventCollector {

    private JavaParserScanEventCollector() {
    }

    static List<ScanEvent> collectSafely(JavaParser parser, Path file, MethodEventExtractor methodEventExtractor) {
        try {
            return collect(parser, file, methodEventExtractor);
        } catch (IOException | RuntimeException ignored) {
            // Ignore parsing issues to keep scanning resilient.
            return List.of();
        }
    }

    private static List<ScanEvent> collect(JavaParser parser, Path file, MethodEventExtractor methodEventExtractor) throws IOException {
        return parser.parse(file).getResult()
            .map(cu -> {
                String pkg = cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
                return cu.findAll(MethodDeclaration.class)
                    .stream()
                    .map(md -> methodEventExtractor.collectMethodEvents(md, pkg))
                    .flatMap(List::stream)
                    .toList();
            })
            .orElseGet(List::of);
    }
}
