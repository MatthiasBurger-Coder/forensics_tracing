package de.burger.forensics.adapters.javaparser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
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
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JavaParser-backed implementation of {@link CodeScanPort}.
 */
public final class JavaParserScanner implements CodeScanPort {

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
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".java")) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        CompilationUnit cu = StaticJavaParser.parse(file);
                        String pkg = cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
                        cu.findAll(MethodDeclaration.class).forEach(md -> collectMethodEvents(md, pkg, events));
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

    private void collectMethodEvents(MethodDeclaration declaration,
                                     String pkg,
                                     List<ScanEvent> events) {
        String typeName = resolveEnclosingType(declaration);
        if (typeName.isEmpty()) {
            return;
        }
        String fqcn = pkg.isEmpty() ? typeName : pkg + "." + typeName;
        String methodName = declaration.getNameAsString();
        String signature = declaration.getSignature().asString();

        declaration.findAll(IfStmt.class).forEach(ifStmt -> {
            IfStmt current = ifStmt;
            while (current != null) {
                var condition = current.getCondition();
                int line = condition.getBegin().map(p -> p.line).orElse(-1);
                SourceLocation location = new SourceLocation(fqcn, methodName, line);
                events.add(new ScanEvent(location, signature, RuleTemplate.IF_TRUE, condition.toString(), "java"));
                if (current.getElseStmt().isPresent()) {
                    events.add(new ScanEvent(location, signature, RuleTemplate.IF_FALSE, condition.toString(), "java"));
                }
                var elseStmt = current.getElseStmt().orElse(null);
                if (elseStmt instanceof IfStmt next) {
                    current = next;
                } else {
                    current = null;
                }
            }
        });

        declaration.findAll(SwitchStmt.class).forEach(sw -> {
            int line = sw.getSelector().getBegin().map(p -> p.line).orElse(-1);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            events.add(new ScanEvent(location, signature, RuleTemplate.SWITCH, sw.getSelector().toString(), "java"));
        });

        declaration.findAll(SwitchEntry.class).forEach(entry -> {
            int line = entry.getBegin().map(p -> p.line).orElse(-1);
            String label = entry.getLabels().isEmpty()
                ? "default"
                : entry.getLabels().stream()
                    .map(Node::toString)
                    .map(String::trim)
                    .collect(Collectors.joining(" | "));
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            events.add(new ScanEvent(location, signature, RuleTemplate.SWITCH_CASE, label, "java"));
        });

        declaration.findAll(ReturnStmt.class).forEach(ret -> {
            int line = ret.getBegin().map(p -> p.line).orElse(-1);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            events.add(new ScanEvent(location, signature, RuleTemplate.RETURN, ret.toString(), "java"));
        });

        declaration.findAll(ThrowStmt.class).forEach(th -> {
            int line = th.getBegin().map(p -> p.line).orElse(-1);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            events.add(new ScanEvent(location, signature, RuleTemplate.THROW, th.getExpression().toString(), "java"));
        });
    }

    private String resolveEnclosingType(MethodDeclaration declaration) {
        LinkedList<String> parts = new LinkedList<>();
        Node current = declaration.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration cls) {
                parts.addFirst(cls.getNameAsString());
            } else if (current instanceof EnumDeclaration en) {
                parts.addFirst(en.getNameAsString());
            } else if (current instanceof RecordDeclaration rec) {
                parts.addFirst(rec.getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        return String.join("$", parts);
    }
}
