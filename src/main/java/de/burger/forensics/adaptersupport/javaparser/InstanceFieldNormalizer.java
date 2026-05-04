package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.CatchClause;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Normalizes {@link NameExpr} usages that refer to instance fields.
 */
public final class InstanceFieldNormalizer {

    Set<Range> identifyInstanceFieldRanges(Node scope, Set<String> localVariables) {
        Set<Range> ranges = new LinkedHashSet<>();
        scope.walk(NameExpr.class, name -> {
            if (isLikelyInstanceField(name, name.getNameAsString(), localVariables) || resolvesToInstanceField(name)) {
                name.getRange().ifPresent(ranges::add);
            }
        });
        return ranges;
    }

    void promoteInstanceFieldAccess(NameExpr name, Set<Range> instanceFieldRanges) {
        if (name.getRange().filter(instanceFieldRanges::contains).isPresent()) {
            String identifier = name.getNameAsString();
            name.replace(new FieldAccessExpr(new NameExpr("$this"), identifier));
        }
    }

    boolean resolvesToInstanceField(NameExpr name) {
        return JavaParserResolutionGuard.resolve(() -> {
            var resolved = name.resolve();
            return resolved.isField() && !resolved.asField().isStatic();
        }).orElse(false);
    }

    boolean isLikelyInstanceField(NameExpr name, String identifier, Set<String> localVariables) {
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
        if (localVariables.contains(identifier)) {
            return false;
        }
        return declaresInstanceField(name, identifier);
    }

    boolean shadowsParameter(NameExpr name, String identifier) {
        return findAncestor(name, MethodDeclaration.class)
            .map(MethodDeclaration::getParameters)
            .map(params -> params.stream().map(Parameter::getNameAsString).anyMatch(identifier::equals))
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

    boolean declaresInstanceField(NameExpr name, String identifier) {
        if (identifier.isEmpty()) {
            return false;
        }
        return findAncestor(name, ClassOrInterfaceDeclaration.class)
            .map(decl -> decl.getFields().stream()
                .filter(field -> !field.isStatic())
                .flatMap(field -> field.getVariables().stream())
                .map(VariableDeclarator::getNameAsString)
                .anyMatch(identifier::equals))
            .or(() -> findAncestor(name, EnumDeclaration.class)
                .map(decl -> decl.getFields().stream()
                    .filter(field -> !field.isStatic())
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
}
