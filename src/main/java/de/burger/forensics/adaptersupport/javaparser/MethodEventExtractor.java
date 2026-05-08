package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import de.burger.forensics.domain.model.ConditionDiagnostic;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts {@link ScanEvent}s from JavaParser AST nodes.
 */
public record MethodEventExtractor(ConditionRenderingStrategy renderingStrategy,
                                   ConditionDiagnosticFactory diagnosticFactory) {

    public MethodEventExtractor(ConditionRenderingStrategy renderingStrategy) {
        this(renderingStrategy, new ConditionDiagnosticFactory());
    }

    public List<ScanEvent> collectMethodEvents(MethodDeclaration declaration, String pkg) {
        List<ScanEvent> events = new ArrayList<>();
        String typeName = resolveEnclosingType(declaration);
        if (typeName.isEmpty()) {
            return events;
        }
        String fqcn = pkg.isEmpty() ? typeName : pkg + "." + typeName;
        String methodName = declaration.getNameAsString();
        String signature = declaration.getSignature().asString();
        String returnType = declaration.getType().asString();

        MethodScanContext context = methodContext(declaration, pkg);

        declaration.findAll(IfStmt.class).forEach(ifStmt -> {
            IfStmt current = ifStmt;
            while (current != null) {
                var condition = current.getCondition();
                int line = condition.getBegin().map(p -> p.line).orElse(-1);
                SourceLocation location = new SourceLocation(fqcn, methodName, line);
                String renderedCondition = renderingStrategy.renderCondition(condition, context);
                List<ConditionDiagnostic> diagnostics = diagnosticFactory.diagnostics(
                        renderedCondition,
                        location,
                        context);
                events.add(new ScanEvent(
                        location,
                        signature,
                        RuleTemplate.IF_TRUE,
                        renderedCondition,
                        "java",
                        returnType,
                        diagnostics));
                events.add(new ScanEvent(
                        location,
                        signature,
                        RuleTemplate.IF_FALSE,
                        renderedCondition,
                        "java",
                        returnType,
                        diagnostics));
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
            String selector = renderingStrategy.renderCondition(sw.getSelector(), context);
            events.add(new ScanEvent(location, signature, RuleTemplate.SWITCH, selector, "java", returnType));
        });

        declaration.findAll(SwitchEntry.class).forEach(entry -> {
            int line = entry.getBegin().map(p -> p.line).orElse(-1);
            String label = renderingStrategy.renderSwitchLabel(entry, context);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            events.add(new ScanEvent(location, signature, RuleTemplate.SWITCH_CASE, label, "java", returnType));
        });

        declaration.findAll(ReturnStmt.class).forEach(ret -> {
            int line = ret.getBegin().map(p -> p.line).orElse(-1);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            String renderedReturn = renderingStrategy.renderReturn(ret, context);
            events.add(new ScanEvent(location, signature, RuleTemplate.RETURN, renderedReturn, "java", returnType));
        });

        declaration.findAll(ThrowStmt.class).forEach(th -> {
            int line = th.getBegin().map(p -> p.line).orElse(-1);
            SourceLocation location = new SourceLocation(fqcn, methodName, line);
            String renderedThrow = renderingStrategy.renderCondition(th.getExpression(), context);
            events.add(new ScanEvent(location, signature, RuleTemplate.THROW, renderedThrow, "java", returnType));
        });

        return events;
    }

    Map<String, Integer> parameterIndexes(MethodDeclaration declaration) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < declaration.getParameters().size(); i++) {
            String name = declaration.getParameter(i).getNameAsString();
            if (!name.isBlank()) {
                indexes.put(name, i + 1);
            }
        }
        return indexes;
    }

    MethodScanContext methodContext(MethodDeclaration declaration) {
        return methodContext(declaration, packageName(declaration));
    }

    MethodScanContext methodContext(MethodDeclaration declaration, String packageName) {
        String sourceTypeName = resolveSourceEnclosingType(declaration);
        return new MethodScanContext(
                declaration,
                parameterIndexes(declaration),
                localVariableNames(declaration),
                importTable(declaration),
                packageName,
                sourceFilePath(declaration),
                sourceTypeName,
                simpleClassName(sourceTypeName),
                declaration.getNameAsString(),
                declaration.getSignature().asString());
    }

    ImportTable importTable(MethodDeclaration declaration) {
        return declaration.findCompilationUnit()
                .map(compilationUnit -> new ImportTable(
                        typeImports(compilationUnit),
                        wildcardTypeImports(compilationUnit),
                        staticMemberImports(compilationUnit),
                        wildcardStaticImports(compilationUnit)))
                .orElseGet(ImportTable::empty);
    }

    Map<String, String> typeImports(MethodDeclaration declaration) {
        return declaration.findCompilationUnit()
                .map(this::typeImports)
                .orElseGet(LinkedHashMap::new);
    }

    Set<String> wildcardTypeImports(MethodDeclaration declaration) {
        return declaration.findCompilationUnit()
                .map(this::wildcardTypeImports)
                .orElseGet(LinkedHashSet::new);
    }

    Map<String, String> staticMemberImports(MethodDeclaration declaration) {
        return declaration.findCompilationUnit()
                .map(this::staticMemberImports)
                .orElseGet(LinkedHashMap::new);
    }

    Set<String> wildcardStaticImports(MethodDeclaration declaration) {
        return declaration.findCompilationUnit()
                .map(this::wildcardStaticImports)
                .orElseGet(LinkedHashSet::new);
    }

    private Map<String, String> typeImports(CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream()
                .filter(importDeclaration -> !importDeclaration.isStatic())
                .filter(importDeclaration -> !importDeclaration.isAsterisk())
                .collect(Collectors.toMap(
                        importDeclaration -> importDeclaration.getName().getIdentifier(),
                        ImportDeclaration::getNameAsString,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Set<String> wildcardTypeImports(CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream()
                .filter(importDeclaration -> !importDeclaration.isStatic())
                .filter(ImportDeclaration::isAsterisk)
                .map(ImportDeclaration::getNameAsString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, String> staticMemberImports(CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream()
                .filter(ImportDeclaration::isStatic)
                .filter(importDeclaration -> !importDeclaration.isAsterisk())
                .collect(Collectors.toMap(
                        importDeclaration -> importDeclaration.getName().getIdentifier(),
                        ImportDeclaration::getNameAsString,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Set<String> wildcardStaticImports(CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream()
                .filter(ImportDeclaration::isStatic)
                .filter(ImportDeclaration::isAsterisk)
                .map(ImportDeclaration::getNameAsString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<String> localVariableNames(MethodDeclaration declaration) {
        Set<String> locals = declaration.findAll(VariableDeclarator.class, declarator ->
                        declarator.getParentNode().map(parent -> !(parent instanceof FieldDeclaration)).orElse(true))
                .stream()
                .map(VariableDeclarator::getNameAsString)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        declaration.findAll(CatchClause.class).stream()
                .map(catchClause -> catchClause.getParameter().getNameAsString())
                .filter(name -> !name.isBlank())
                .forEach(locals::add);

        return locals;
    }

    String resolveEnclosingType(MethodDeclaration declaration) {
        return resolveEnclosingType(declaration, "$");
    }

    String resolveSourceEnclosingType(MethodDeclaration declaration) {
        return resolveEnclosingType(declaration, ".");
    }

    private String resolveEnclosingType(MethodDeclaration declaration, String delimiter) {
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
        return String.join(delimiter, parts);
    }

    private String packageName(MethodDeclaration declaration) {
        return declaration.findCompilationUnit()
                .flatMap(compilationUnit -> compilationUnit.getPackageDeclaration()
                        .map(NodeWithName::getNameAsString))
                .orElse("");
    }

    private String sourceFilePath(MethodDeclaration declaration) {
        return declaration.findCompilationUnit()
                .flatMap(compilationUnit -> compilationUnit.getStorage()
                        .map(storage -> storage.getPath().toString()))
                .orElse("");
    }

    private String simpleClassName(String sourceTypeName) {
        int nestedTypeSeparator = sourceTypeName.lastIndexOf('.');
        return nestedTypeSeparator < 0 ? sourceTypeName : sourceTypeName.substring(nestedTypeSeparator + 1);
    }
}
