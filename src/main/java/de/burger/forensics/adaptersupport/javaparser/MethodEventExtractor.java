package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
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
public final class MethodEventExtractor {

    private final ConditionRenderingStrategy renderingStrategy;

    public MethodEventExtractor(ConditionRenderingStrategy renderingStrategy) {
        this.renderingStrategy = renderingStrategy;
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

        MethodScanContext context = new MethodScanContext(declaration, parameterIndexes(declaration), localVariableNames(declaration));

        declaration.findAll(IfStmt.class).forEach(ifStmt -> {
            IfStmt current = ifStmt;
            while (current != null) {
                var condition = current.getCondition();
                int line = condition.getBegin().map(p -> p.line).orElse(-1);
                SourceLocation location = new SourceLocation(fqcn, methodName, line);
                String renderedCondition = renderingStrategy.renderCondition(condition, context);
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

    Set<String> localVariableNames(MethodDeclaration declaration) {
        return declaration.findAll(VariableDeclarator.class, var ->
                var.getParentNode().map(parent -> !(parent instanceof FieldDeclaration)).orElse(true))
            .stream()
            .map(VariableDeclarator::getNameAsString)
            .filter(name -> !name.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    String resolveEnclosingType(MethodDeclaration declaration) {
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
}
