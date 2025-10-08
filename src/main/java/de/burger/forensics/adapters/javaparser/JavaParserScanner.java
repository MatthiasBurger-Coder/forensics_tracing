package de.burger.forensics.adapters.javaparser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.CatchClause;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Nonnull;

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
        String returnType = declaration.getType().asString();

        Map<String, Integer> parameterIndexes = parameterIndexes(declaration);

        declaration.findAll(IfStmt.class).forEach(ifStmt -> {
            IfStmt current = ifStmt;
            while (current != null) {
                var condition = current.getCondition();
                int line = condition.getBegin().map(p -> p.line).orElse(-1);
                SourceLocation location = new SourceLocation(fqcn, methodName, line);
                String renderedCondition = renderCondition(condition, parameterIndexes);
                events.add(new ScanEvent(location, signature, RuleTemplate.IF_TRUE, renderedCondition, "java", returnType));
                events.add(new ScanEvent(location, signature, RuleTemplate.IF_FALSE, renderedCondition, "java", returnType));
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
            String selector = renderCondition(sw.getSelector(), parameterIndexes);
            events.add(new ScanEvent(location, signature, RuleTemplate.SWITCH, selector, "java", returnType));
        });

        declaration.findAll(SwitchEntry.class).forEach(entry -> {
            int line = entry.getBegin().map(p -> p.line).orElse(-1);
            String label = renderSwitchLabel(entry, parameterIndexes);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            events.add(new ScanEvent(location, signature, RuleTemplate.SWITCH_CASE, label, "java", returnType));
        });

        declaration.findAll(ReturnStmt.class).forEach(ret -> {
            int line = ret.getBegin().map(p -> p.line).orElse(-1);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            String renderedReturn = renderReturn(ret, parameterIndexes);
            events.add(new ScanEvent(location, signature, RuleTemplate.RETURN, renderedReturn, "java", returnType));
        });

        declaration.findAll(ThrowStmt.class).forEach(th -> {
            int line = th.getBegin().map(p -> p.line).orElse(-1);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            String renderedThrow = renderCondition(th.getExpression(), parameterIndexes);
            events.add(new ScanEvent(location, signature, RuleTemplate.THROW, renderedThrow, "java", returnType));
        });
    }

    private String resolveEnclosingType(MethodDeclaration declaration) {
        LinkedList<String> parts = new LinkedList<>();
        Node current = declaration.getParentNode().orElse(null);
        while (current != null) {
            switch (current) {
                case ClassOrInterfaceDeclaration cls -> parts.addFirst(cls.getNameAsString());
                case EnumDeclaration en -> parts.addFirst(en.getNameAsString());
                case RecordDeclaration rec -> parts.addFirst(rec.getNameAsString());
                default -> {
                }
            }
            current = current.getParentNode().orElse(null);
        }
        return String.join("$", parts);
    }

    private Map<String, Integer> parameterIndexes(MethodDeclaration declaration) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < declaration.getParameters().size(); i++) {
            String name = declaration.getParameter(i).getNameAsString();
            if (!name.isBlank()) {
                indexes.put(name, i + 1);
            }
        }
        return indexes;
    }

    private String renderCondition(Expression expression, Map<String, Integer> parameterIndexes) {
        return sanitizeExpression(expression, parameterIndexes).toString();
    }

    private String renderReturn(ReturnStmt stmt, Map<String, Integer> parameterIndexes) {
        ReturnStmt clone = stmt.clone();
        clone.getExpression().ifPresent(expr -> clone.setExpression(sanitizeExpression(expr, parameterIndexes)));
        return clone.toString();
    }

    private String renderSwitchLabel(SwitchEntry entry, Map<String, Integer> parameterIndexes) {
        SwitchEntry clone = entry.clone();
        for (int i = 0; i < clone.getLabels().size(); i++) {
            Expression label = clone.getLabels().get(i);
            clone.getLabels().set(i, sanitizeExpression(label, parameterIndexes));
        }
        if (clone.getLabels().isEmpty()) {
            return "default";
        }
        return clone.getLabels().stream()
                .map(Node::toString)
                .map(String::trim)
                .collect(Collectors.joining(" | "));
    }

    private Expression sanitizeExpression(Expression expression, Map<String, Integer> parameterIndexes) {
        Set<Range> instanceFieldRanges = identifyInstanceFieldRanges(expression);
        Expression clone = expression.clone();
        clone.walk(NameExpr.class, name -> {
            Integer index = parameterIndexes.get(name.getNameAsString());
            if (index != null) {
                name.setName("$" + index);
                return;
            }
            promoteInstanceFieldAccess(name, instanceFieldRanges);
        });
        return clone;
    }

    private void promoteInstanceFieldAccess(NameExpr name, Set<Range> instanceFieldRanges) {
        if (name.getRange().filter(instanceFieldRanges::contains).isPresent()) {
            String identifier = name.getNameAsString();
            name.replace(new FieldAccessExpr(new NameExpr("$this"), identifier));
        }
    }

    private Set<Range> identifyInstanceFieldRanges(Expression expression) {
        Set<Range> ranges = new LinkedHashSet<>();
        expression.walk(NameExpr.class, name -> {
            if (resolvesToInstanceField(name) || isLikelyInstanceField(name, name.getNameAsString())) {
                name.getRange().ifPresent(ranges::add);
            }
        });
        return ranges;
    }

    private boolean resolvesToInstanceField(NameExpr name) {
        try {
            var resolved = name.resolve();
            return resolved.isField() && !resolved.asField().isStatic();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isLikelyInstanceField(NameExpr name, String identifier) {
        if (identifier.isEmpty()) {
            return false;
        }
        if (name.getParentNode().filter(FieldAccessExpr.class::isInstance).isPresent()) {
            return false;
        }
        if (shadowsParameter(name, identifier)) {
            return false;
        }
        if (shadowsLambdaParameter(name, identifier)) {
            return false;
        }
        if (shadowsCatchParameter(name, identifier)) {
            return false;
        }
        if (hasLocalVariable(name, identifier)) {
            return false;
        }
        return declaresInstanceField(name, identifier);
    }

    private boolean shadowsParameter(NameExpr name, String identifier) {
        return name.findAncestor(MethodDeclaration.class)
            .map(MethodDeclaration::getParameters)
            .map(params -> params.stream().map(Parameter::getNameAsString).anyMatch(identifier::equals))
            .orElse(false);
    }

    private boolean shadowsLambdaParameter(NameExpr name, String identifier) {
        return name.findAncestor(LambdaExpr.class)
            .map(lambda -> lambda.getParameters().stream()
                .map(Parameter::getNameAsString)
                .anyMatch(identifier::equals))
            .orElse(false);
    }

    private boolean shadowsCatchParameter(NameExpr name, String identifier) {
        return name.findAncestor(CatchClause.class)
            .map(catchClause -> catchClause.getParameter().getNameAsString().equals(identifier))
            .orElse(false);
    }

    private boolean hasLocalVariable(NameExpr name, String identifier) {
        return name.findAncestor(MethodDeclaration.class)
            .map(method -> method.findAll(VariableDeclarator.class, var ->
                var.getNameAsString().equals(identifier)
                    && var.getParentNode().map(parent -> !(parent instanceof FieldDeclaration)).orElse(true)))
            .map(list -> !list.isEmpty())
            .orElse(false);
    }

    private boolean declaresInstanceField(NameExpr name, String identifier) {
        if (identifier.isEmpty()) {
            return false;
        }
        return name.findAncestor(ClassOrInterfaceDeclaration.class)
            .map(decl -> decl.getFields().stream()
                .filter(field -> !field.isStatic())
                .flatMap(field -> field.getVariables().stream())
                .map(VariableDeclarator::getNameAsString)
                .anyMatch(identifier::equals))
            .or(() -> name.findAncestor(EnumDeclaration.class)
                .map(decl -> decl.getFields().stream()
                    .filter(field -> !field.isStatic())
                    .flatMap(field -> field.getVariables().stream())
                    .map(VariableDeclarator::getNameAsString)
                    .anyMatch(identifier::equals)))
            .or(() -> name.findAncestor(RecordDeclaration.class)
                .map(decl -> decl.getFields().stream()
                    .flatMap(field -> field.getVariables().stream())
                    .map(VariableDeclarator::getNameAsString)
                    .anyMatch(identifier::equals)))
            .orElse(false);
    }
}
