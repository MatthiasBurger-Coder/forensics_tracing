package de.burger.forensics.adapters.javaparser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import de.burger.forensics.adaptersupport.javaparser.DefaultConditionRenderingStrategy;
import de.burger.forensics.adaptersupport.javaparser.InstanceFieldNormalizer;
import de.burger.forensics.adaptersupport.javaparser.MethodEventExtractor;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.port.out.CodeScanPort;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import jakarta.annotation.Nonnull;

/**
 * JavaParser-backed implementation of {@link CodeScanPort}.
 */
public final class JavaParserScanner implements CodeScanPort {

    private final MethodEventExtractor methodEventExtractor;

    public JavaParserScanner() {
        this(new MethodEventExtractor(new DefaultConditionRenderingStrategy(new InstanceFieldNormalizer())));
    }

    public JavaParserScanner(MethodEventExtractor methodEventExtractor) {
        this.methodEventExtractor = methodEventExtractor;
    }

    @Override
    public Stream<ScanEvent> scan(Path root) {
        List<ScanEvent> events = new ArrayList<>();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
        if (Files.isDirectory(root)) {
            typeSolver.add(new JavaParserTypeSolver(root));
        } else {
            Path parent = root.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                typeSolver.add(new JavaParserTypeSolver(parent));
            }
        }
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(configuration);

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 64, new SimpleFileVisitor<>() {
                @Override
                public @Nonnull FileVisitResult preVisitDirectory(@Nonnull Path dir, @Nonnull BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @Nonnull FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".java")) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        CompilationUnit cu = StaticJavaParser.parse(file);
                        String pkg = cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
                        cu.findAll(MethodDeclaration.class)
                            .stream()
                            .map(md -> methodEventExtractor.collectMethodEvents(md, pkg))
                            .forEach(events::addAll);
                    } catch (IOException | RuntimeException ignored) {
                        // Ignore parsing issues to keep scanning resilient.
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Ignore traversal failures to avoid failing the build on single-file issues.
        }
        return events.stream();
    }
}
