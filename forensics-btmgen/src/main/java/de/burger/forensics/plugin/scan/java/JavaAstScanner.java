package de.burger.forensics.plugin.scan.java;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import de.burger.forensics.plugin.scan.ScanEvent;
import de.burger.forensics.plugin.scan.SourceScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// English comments only in code.
public final class JavaAstScanner implements SourceScanner {

    @Override
    public List<ScanEvent> scan(Path root, List<String> includePkgs, List<String> excludePkgs) {
        var out = new ArrayList<ScanEvent>();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
        if (Files.isDirectory(root)) {
            typeSolver.add(new JavaParserTypeSolver(root));
        } else {
            Path parent = root.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                typeSolver.add(new JavaParserTypeSolver(parent));
            }
        }
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        try (var stream = Files.walk(root)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(path);
                    String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
                    if (!include(pkg, includePkgs) || exclude(pkg, excludePkgs)) {
                        continue;
                    }
                    cu.findAll(MethodDeclaration.class).forEach(md -> collectMethodEvents(md, out, pkg));
                } catch (IOException | RuntimeException ignored) {
                    // Ignore parsing failures for now.
                }
            }
        } catch (IOException ignored) {
            // Ignore traversal failures for now.
        }
        return out;
    }

    private void collectMethodEvents(MethodDeclaration declaration, List<ScanEvent> out, String pkg) {
        String typeName = resolveEnclosingType(declaration);
        if (typeName.isEmpty()) {
            return;
        }
        String fqcn = pkg.isEmpty() ? typeName : pkg + "." + typeName;
        String methodName = declaration.getNameAsString();
        String signature = declaration.getSignature().asString();

        declaration.findAll(IfStmt.class).forEach(stmt -> {
            String cond = stmt.getCondition().toString();
            int line = stmt.getCondition().getBegin().map(p -> p.line).orElse(-1);
            out.add(new ScanEvent("java", fqcn, methodName, signature, "if-true", line, cond));
            if (stmt.getElseStmt().isPresent()) {
                out.add(new ScanEvent("java", fqcn, methodName, signature, "if-false", line, cond));
            }
        });

        declaration.findAll(SwitchStmt.class).forEach(sw -> {
            int line = sw.getSelector().getBegin().map(p -> p.line).orElse(-1);
            String selector = sw.getSelector().toString();
            out.add(new ScanEvent("java", fqcn, methodName, signature, "switch", line, selector));
        });

        declaration.findAll(SwitchEntry.class).forEach(entry -> {
            int line = entry.getBegin().map(p -> p.line).orElse(-1);
            String label = entry.getLabels().isEmpty()
                ? "default"
                : entry.getLabels().stream().map(Object::toString).collect(Collectors.joining(" | "));
            out.add(new ScanEvent("java", fqcn, methodName, signature, "switch-case", line, label));
        });

        declaration.findAll(ReturnStmt.class).forEach(stmt -> {
            int line = stmt.getBegin().map(p -> p.line).orElse(-1);
            out.add(new ScanEvent("java", fqcn, methodName, signature, "return", line, stmt.getExpression().map(Object::toString).orElse(null)));
        });

        declaration.findAll(ThrowStmt.class).forEach(stmt -> {
            int line = stmt.getBegin().map(p -> p.line).orElse(-1);
            out.add(new ScanEvent("java", fqcn, methodName, signature, "throw", line, stmt.getExpression().toString()));
        });
    }

    private boolean include(String pkg, List<String> includes) {
        return includes == null || includes.isEmpty() || includes.stream().anyMatch(pkg::startsWith);
    }

    private boolean exclude(String pkg, List<String> excludes) {
        return excludes != null && excludes.stream().anyMatch(pkg::startsWith);
    }

    private String resolveEnclosingType(MethodDeclaration declaration) {
        var parts = new java.util.ArrayList<String>();
        Node current = declaration.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration classDecl) {
                parts.add(0, classDecl.getNameAsString());
            } else if (current instanceof EnumDeclaration enumDecl) {
                parts.add(0, enumDecl.getNameAsString());
            } else if (current instanceof RecordDeclaration recordDecl) {
                parts.add(0, recordDecl.getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        return String.join("$", parts);
    }
}
