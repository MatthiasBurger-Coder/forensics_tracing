package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.CatchClause;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Qualifies {@link NameExpr} usages that refer to static fields with "$CLASS".
 */
public final class StaticFieldQualifier {

    Set<Range> identifyStaticFieldRanges(Node scope, Set<String> localVariables) {
        Set<Range> ranges = new LinkedHashSet<>();
        scope.walk(NameExpr.class, name -> {
            if (isLikelyStaticField(name, name.getNameAsString(), localVariables) || resolvesToStaticField(name)) {
                name.getRange().ifPresent(ranges::add);
            }
        });
        return ranges;
    }

    boolean qualifyStaticFieldAccess(NameExpr name, Set<Range> staticFieldRanges) {
        if (name.getRange().filter(staticFieldRanges::contains).isPresent()) {
            String identifier = name.getNameAsString();
            name.replace(new FieldAccessExpr(new NameExpr("$CLASS"), identifier));
            return true;
        }
        return false;
    }

    boolean resolvesToStaticField(NameExpr name) {
        return JavaParserResolutionGuard.resolve(() -> {
            var resolved = name.resolve();
            return resolved.isField()
                    && resolved.asField().isStatic()
                    && isDeclaredByCurrentType(name, resolved.asField().declaringType().getQualifiedName());
        }).orElse(false);
    }

    boolean isLikelyStaticField(NameExpr name, String identifier, Set<String> localVariables) {
        return !identifier.isEmpty()
            && name.getParentNode().filter(FieldAccessExpr.class::isInstance).isEmpty()
            && !shadowsParameter(name, identifier)
            && !shadowsLambdaParameter(name, identifier)
            && !shadowsCatchParameter(name, identifier)
            && !localVariables.contains(identifier)
            && declaresStaticField(name, identifier);
    }

    boolean shadowsParameter(NameExpr name, String identifier) {
        return findAncestor(name, com.github.javaparser.ast.body.MethodDeclaration.class)
            .map(method -> method.getParameters().stream().map(Parameter::getNameAsString).anyMatch(identifier::equals))
            .orElse(false);
    }

    boolean shadowsLambdaParameter(NameExpr name, String identifier) {
        return findAncestor(name, LambdaExpr.class)
            .map(lambda -> lambda.getParameters().stream()
                .map(Parameter::getNameAsString)
                .anyMatch(identifier::equals))
            .orElse(false);
    }

    boolean shadowsCatchParameter(NameExpr name, String identifier) {
        return findAncestor(name, CatchClause.class)
            .map(catchClause -> catchClause.getParameter().getNameAsString().equals(identifier))
            .orElse(false);
    }

    boolean declaresStaticField(NameExpr name, String identifier) {
        if (identifier.isEmpty()) {
            return false;
        }
        return findAncestor(name, ClassOrInterfaceDeclaration.class)
            .map(decl -> decl.getFields().stream()
                .filter(FieldDeclaration::isStatic)
                .flatMap(field -> field.getVariables().stream())
                .map(VariableDeclarator::getNameAsString)
                .anyMatch(identifier::equals))
            .or(() -> findAncestor(name, EnumDeclaration.class)
                .map(decl -> decl.getFields().stream()
                    .filter(FieldDeclaration::isStatic)
                    .flatMap(field -> field.getVariables().stream())
                    .map(VariableDeclarator::getNameAsString)
                    .anyMatch(identifier::equals)))
            .or(() -> findAncestor(name, RecordDeclaration.class)
                .map(decl -> decl.getFields().stream()
                    .flatMap(field -> field.getVariables().stream())
                    .map(VariableDeclarator::getNameAsString)
                    .anyMatch(identifier::equals)))
            .orElse(false);
    }

    <T extends Node> Optional<T> findAncestor(NameExpr name, Class<T> type) {
        return name.stream(Node.TreeTraversal.PARENTS)
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst();
    }

    private boolean isDeclaredByCurrentType(NameExpr name, String declaringTypeName) {
        return currentTypeName(name)
                .map(declaringTypeName::equals)
                .orElse(false);
    }

    private Optional<String> currentTypeName(NameExpr name) {
        java.util.LinkedList<String> parts = new java.util.LinkedList<>();
        name.stream(Node.TreeTraversal.PARENTS)
                .filter(node -> node instanceof TypeDeclaration<?>)
                .map(node -> (TypeDeclaration<?>) node)
                .map(TypeDeclaration::getNameAsString)
                .forEach(parts::addFirst);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        String typeName = String.join(".", parts);
        String packageName = name.findCompilationUnit()
                .flatMap(compilationUnit -> compilationUnit.getPackageDeclaration()
                        .map(packageDeclaration -> packageDeclaration.getNameAsString()))
                .orElse("");
        return Optional.of(packageName.isBlank() ? typeName : packageName + "." + typeName);
    }
}
