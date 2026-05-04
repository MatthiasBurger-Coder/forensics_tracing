package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default source-expression qualifier used before rendering Byteman conditions.
 */
public final class DefaultTypeReferenceQualifier implements TypeReferenceQualifier {

    private final InstanceFieldNormalizer instanceFieldNormalizer;
    private final StaticFieldQualifier staticFieldQualifier;

    public DefaultTypeReferenceQualifier(InstanceFieldNormalizer instanceFieldNormalizer,
                                         StaticFieldQualifier staticFieldQualifier) {
        this.instanceFieldNormalizer = Objects.requireNonNull(instanceFieldNormalizer, "instanceFieldNormalizer");
        this.staticFieldQualifier = Objects.requireNonNull(staticFieldQualifier, "staticFieldQualifier");
    }

    @Override
    public QualifiedExpression qualify(Expression expression, MethodScanContext context) {
        Set<Range> instanceFieldRanges = instanceFieldNormalizer.identifyInstanceFieldRanges(
                expression,
                context.localVariables());
        Set<Range> staticFieldRanges = staticFieldQualifier.identifyStaticFieldRanges(
                expression,
                context.localVariables());
        Map<Range, String> resolvedStaticMethodScopes = resolvedStaticMethodScopes(expression, context);
        Map<Range, String> resolvedFieldScopes = resolvedFieldScopes(expression, context);
        Map<Range, String> resolvedStaticValues = resolvedStaticValues(expression, context);
        Expression clone = expression.clone();
        clone.walk(MethodCallExpr.class, methodCall -> {
            if (qualifyStaticImportedMethod(methodCall, context)) {
                return;
            }
            methodCall.getRange()
                    .map(resolvedStaticMethodScopes::get)
                    .ifPresent(scope -> methodCall.setScope(StaticJavaParser.parseExpression(scope)));
        });
        clone.walk(FieldAccessExpr.class, fieldAccess -> fieldAccess.getRange()
                .map(resolvedFieldScopes::get)
                .ifPresent(scope -> fieldAccess.setScope(StaticJavaParser.parseExpression(scope))));
        clone.walk(NameExpr.class, name -> {
            Integer index = context.parameterIndex(name.getNameAsString());
            if (index != null) {
                name.setName("$" + index);
                return;
            }
            if (context.isLocalVariable(name.getNameAsString())) {
                name.setName("$" + name.getNameAsString());
                return;
            }
            if (qualifyImportedName(name, context)) {
                return;
            }
            String resolvedStaticValue = name.getRange()
                    .map(resolvedStaticValues::get)
                    .orElse(null);
            if (resolvedStaticValue != null) {
                name.replace(StaticJavaParser.parseExpression(resolvedStaticValue));
                return;
            }
            if (staticFieldQualifier.qualifyStaticFieldAccess(name, staticFieldRanges)) {
                return;
            }
            instanceFieldNormalizer.promoteInstanceFieldAccess(name, instanceFieldRanges);
        });
        return new QualifiedExpression(clone);
    }

    private static boolean qualifyImportedName(NameExpr name, MethodScanContext context) {
        String identifier = name.getNameAsString();
        String staticMember = context.staticMemberImport(identifier);
        if (staticMember != null) {
            name.replace(StaticJavaParser.parseExpression(staticMember));
            return true;
        }

        String typeImport = context.typeImport(identifier);
        if (typeImport != null) {
            name.replace(StaticJavaParser.parseExpression(typeImport));
            return true;
        }

        return false;
    }

    private static boolean qualifyStaticImportedMethod(MethodCallExpr methodCall, MethodScanContext context) {
        if (methodCall.getScope().isPresent()) {
            return false;
        }

        String staticMember = context.staticMemberImport(methodCall.getNameAsString());
        if (staticMember == null) {
            return false;
        }

        int memberSeparator = staticMember.lastIndexOf('.');
        if (memberSeparator <= 0) {
            return false;
        }

        methodCall.setScope(StaticJavaParser.parseExpression(staticMember.substring(0, memberSeparator)));
        return true;
    }

    private static Map<Range, String> resolvedStaticMethodScopes(Expression expression, MethodScanContext context) {
        Map<Range, String> scopes = new LinkedHashMap<>();
        expression.walk(MethodCallExpr.class, methodCall -> resolvedStaticMethodScope(methodCall, context)
                .ifPresent(scope -> methodCall.getRange().ifPresent(range -> scopes.put(range, scope))));
        return scopes;
    }

    private static java.util.Optional<String> resolvedStaticMethodScope(
            MethodCallExpr methodCall,
            MethodScanContext context) {
        if (methodCall.getScope()
                .filter(NameExpr.class::isInstance)
                .map(NameExpr.class::cast)
                .map(NameExpr::getNameAsString)
                .filter(context::hasAmbiguousWildcardTypeCandidate)
                .isPresent()) {
            return java.util.Optional.empty();
        }
        if (methodCall.getScope().isEmpty()
                && context.hasAmbiguousWildcardStaticCandidate(methodCall.getNameAsString())) {
            return java.util.Optional.empty();
        }
        return JavaParserResolutionGuard.resolve(() -> {
            var resolved = methodCall.resolve();
            if (resolved.isStatic()) {
                return resolved.declaringType().getQualifiedName();
            }
            return null;
        }).flatMap(declaringType -> externalTypeName(declaringType, context));
    }

    private static Map<Range, String> resolvedFieldScopes(Expression expression, MethodScanContext context) {
        Map<Range, String> scopes = new LinkedHashMap<>();
        expression.walk(FieldAccessExpr.class, fieldAccess -> resolvedFieldScope(fieldAccess, context)
                .ifPresent(scope -> fieldAccess.getRange().ifPresent(range -> scopes.put(range, scope))));
        return scopes;
    }

    private static java.util.Optional<String> resolvedFieldScope(FieldAccessExpr fieldAccess, MethodScanContext context) {
        if (fieldAccess.getScope() instanceof NameExpr scope
                && context.hasAmbiguousWildcardTypeCandidate(scope.getNameAsString())) {
            return java.util.Optional.empty();
        }
        return JavaParserResolutionGuard.resolve(() -> declaringTypeName(fieldAccess.resolve()))
                .flatMap(declaringType -> externalTypeName(declaringType, context));
    }

    private static Map<Range, String> resolvedStaticValues(Expression expression, MethodScanContext context) {
        Map<Range, String> values = new LinkedHashMap<>();
        expression.walk(NameExpr.class, name -> resolvedStaticValue(name, context)
                .ifPresent(value -> name.getRange().ifPresent(range -> values.put(range, value))));
        return values;
    }

    private static java.util.Optional<String> resolvedStaticValue(NameExpr name, MethodScanContext context) {
        if (context.hasAmbiguousWildcardStaticCandidate(name.getNameAsString())) {
            return java.util.Optional.empty();
        }
        return JavaParserResolutionGuard.resolve(() -> {
            ResolvedValueDeclaration resolved = name.resolve();
            return externalTypeName(declaringTypeName(resolved), context)
                    .map(declaringType -> declaringType + "." + resolved.getName())
                    .orElse(null);
        });
    }

    private static String declaringTypeName(ResolvedValueDeclaration resolved) {
        if (resolved.isField() && resolved.asField().isStatic()) {
            return resolved.asField().declaringType().getQualifiedName();
        }
        if (resolved.isEnumConstant() && resolved.getType().isReferenceType()) {
            return resolved.getType().asReferenceType().getQualifiedName();
        }
        return null;
    }

    private static java.util.Optional<String> externalTypeName(String declaringType, MethodScanContext context) {
        if (declaringType == null || declaringType.isBlank() || context.isCurrentSourceType(declaringType)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(declaringType);
    }
}
