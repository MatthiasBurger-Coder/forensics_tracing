package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.Type;
import de.burger.forensics.domain.model.cache.DependencyKind;
import de.burger.forensics.domain.model.cache.ScanDependency;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Extracts syntactic dependency descriptors from JavaParser compilation units.
 */
public final class JavaParserDependencyExtractor {

    public List<ScanDependency> extract(CompilationUnit compilationUnit, String sourceRelativePath) {
        Objects.requireNonNull(compilationUnit, "Compilation unit must not be null.");
        if (sourceRelativePath == null || sourceRelativePath.isBlank()) {
            throw new IllegalArgumentException("Source relative path must not be blank.");
        }

        Set<ScanDependency> dependencies = new LinkedHashSet<>();
        String packageName = packageName(compilationUnit);
        String fallbackOwnerType = fallbackOwnerType(compilationUnit, packageName, sourceRelativePath);

        compilationUnit.getImports().forEach(importDeclaration ->
                add(dependencies, DependencyKind.IMPORT, sourceRelativePath, ownerType(importDeclaration, packageName, fallbackOwnerType),
                        "", importTarget(importDeclaration), importDeclaration));

        compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(typeDeclaration -> {
            typeDeclaration.getExtendedTypes().forEach(type ->
                    add(dependencies, DependencyKind.EXTENDS, sourceRelativePath, ownerType(typeDeclaration, packageName, fallbackOwnerType),
                            "", type.asString(), type));
            typeDeclaration.getImplementedTypes().forEach(type ->
                    add(dependencies, DependencyKind.IMPLEMENTS, sourceRelativePath, ownerType(typeDeclaration, packageName, fallbackOwnerType),
                            "", type.asString(), type));
        });

        compilationUnit.findAll(EnumDeclaration.class).forEach(enumDeclaration ->
                enumDeclaration.getImplementedTypes().forEach(type ->
                        add(dependencies, DependencyKind.IMPLEMENTS, sourceRelativePath,
                                ownerType(enumDeclaration, packageName, fallbackOwnerType), "", type.asString(), type)));

        compilationUnit.findAll(RecordDeclaration.class).forEach(recordDeclaration ->
                recordDeclaration.getImplementedTypes().forEach(type ->
                        add(dependencies, DependencyKind.IMPLEMENTS, sourceRelativePath,
                                ownerType(recordDeclaration, packageName, fallbackOwnerType), "", type.asString(), type)));

        compilationUnit.findAll(AnnotationExpr.class).forEach(annotation ->
                add(dependencies, DependencyKind.ANNOTATION, sourceRelativePath, ownerType(annotation, packageName, fallbackOwnerType),
                        ownerMember(annotation), annotation.getNameAsString(), annotation));

        compilationUnit.findAll(MethodDeclaration.class).forEach(methodDeclaration -> {
            methodDeclaration.getThrownExceptions().forEach(type ->
                    add(dependencies, DependencyKind.THROWN_TYPE, sourceRelativePath,
                            ownerType(methodDeclaration, packageName, fallbackOwnerType), methodDeclaration.getNameAsString(),
                            type.asString(), type));
            addReturnType(dependencies, sourceRelativePath, packageName, fallbackOwnerType, methodDeclaration);
            methodDeclaration.getParameters().forEach(parameter ->
                    addParameterType(dependencies, sourceRelativePath, packageName, fallbackOwnerType, parameter));
        });

        compilationUnit.findAll(ConstructorDeclaration.class).forEach(constructorDeclaration -> {
            constructorDeclaration.getThrownExceptions().forEach(type ->
                    add(dependencies, DependencyKind.THROWN_TYPE, sourceRelativePath,
                            ownerType(constructorDeclaration, packageName, fallbackOwnerType), constructorDeclaration.getNameAsString(),
                            type.asString(), type));
            constructorDeclaration.getParameters().forEach(parameter ->
                    addParameterType(dependencies, sourceRelativePath, packageName, fallbackOwnerType, parameter));
        });

        compilationUnit.findAll(CompactConstructorDeclaration.class).forEach(constructorDeclaration ->
                constructorDeclaration.getThrownExceptions().forEach(type ->
                        add(dependencies, DependencyKind.THROWN_TYPE, sourceRelativePath,
                                ownerType(constructorDeclaration, packageName, fallbackOwnerType),
                                constructorDeclaration.getNameAsString(), type.asString(), type)));

        compilationUnit.findAll(RecordDeclaration.class).forEach(recordDeclaration ->
                recordDeclaration.getParameters().forEach(parameter ->
                        addParameterType(dependencies, sourceRelativePath, packageName, fallbackOwnerType, parameter)));

        compilationUnit.findAll(FieldAccessExpr.class).forEach(fieldAccess ->
                add(dependencies, DependencyKind.FIELD_ACCESS, sourceRelativePath, ownerType(fieldAccess, packageName, fallbackOwnerType),
                        ownerMember(fieldAccess), fieldAccess.toString(), fieldAccess));

        compilationUnit.findAll(MethodCallExpr.class).forEach(methodCall ->
                add(dependencies, DependencyKind.METHOD_CALL, sourceRelativePath, ownerType(methodCall, packageName, fallbackOwnerType),
                        ownerMember(methodCall), methodCallTarget(methodCall), methodCall));

        compilationUnit.findAll(ObjectCreationExpr.class).forEach(objectCreation ->
                add(dependencies, DependencyKind.CONSTRUCTOR_CALL, sourceRelativePath,
                        ownerType(objectCreation, packageName, fallbackOwnerType), ownerMember(objectCreation),
                        objectCreation.getType().getNameWithScope(), objectCreation));

        return List.copyOf(dependencies);
    }

    private void addReturnType(Set<ScanDependency> dependencies,
                               String sourceRelativePath,
                               String packageName,
                               String fallbackOwnerType,
                               MethodDeclaration methodDeclaration) {
        Type returnType = methodDeclaration.getType();
        if (!returnType.isVoidType()) {
            add(dependencies, DependencyKind.RETURN_TYPE, sourceRelativePath,
                    ownerType(methodDeclaration, packageName, fallbackOwnerType), methodDeclaration.getNameAsString(),
                    returnType.asString(), returnType);
        }
    }

    private void addParameterType(Set<ScanDependency> dependencies,
                                  String sourceRelativePath,
                                  String packageName,
                                  String fallbackOwnerType,
                                  Parameter parameter) {
        add(dependencies, DependencyKind.PARAMETER_TYPE, sourceRelativePath,
                ownerType(parameter, packageName, fallbackOwnerType), ownerMember(parameter), parameter.getType().asString(), parameter);
    }

    private void add(Set<ScanDependency> dependencies,
                     DependencyKind kind,
                     String sourceRelativePath,
                     String ownerType,
                     String ownerMember,
                     String target,
                     Node node) {
        String normalizedTarget = Objects.requireNonNullElse(target, "").trim();
        if (!normalizedTarget.isEmpty()) {
            dependencies.add(new ScanDependency(
                    kind,
                    sourceRelativePath,
                    ownerType,
                    ownerMember,
                    normalizedTarget,
                    node.getBegin().map(position -> position.line).orElse(0),
                    node.getBegin().map(position -> position.column).orElse(0)));
        }
    }

    private String packageName(CompilationUnit compilationUnit) {
        return compilationUnit.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
    }

    private String fallbackOwnerType(CompilationUnit compilationUnit, String packageName, String sourceRelativePath) {
        Optional<TypeDeclaration<?>> firstType = compilationUnit.getTypes().stream().findFirst();
        return firstType
                .map(type -> qualifiedTypeName(packageName, type.getNameAsString()))
                .orElseGet(() -> sourceRelativePath.replace('/', '.').replace('\\', '.').replaceAll("\\.java$", ""));
    }

    private String ownerType(Node node, String packageName, String fallbackOwnerType) {
        LinkedList<String> parts = new LinkedList<>();
        Node current = node;
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration declaration) {
                parts.addFirst(declaration.getNameAsString());
            } else if (current instanceof EnumDeclaration declaration) {
                parts.addFirst(declaration.getNameAsString());
            } else if (current instanceof RecordDeclaration declaration) {
                parts.addFirst(declaration.getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        if (parts.isEmpty()) {
            return fallbackOwnerType;
        }
        return qualifiedTypeName(packageName, String.join("$", parts));
    }

    private String ownerMember(Node node) {
        return findAncestor(node, MethodDeclaration.class)
                .map(MethodDeclaration::getNameAsString)
                .or(() -> findAncestor(node, ConstructorDeclaration.class).map(ConstructorDeclaration::getNameAsString))
                .or(() -> findAncestor(node, CompactConstructorDeclaration.class)
                        .map(CompactConstructorDeclaration::getNameAsString))
                .orElse("");
    }

    private <T extends Node> Optional<T> findAncestor(Node node, Class<T> type) {
        Node current = node.getParentNode().orElse(null);
        while (current != null) {
            if (type.isInstance(current)) {
                return Optional.of(type.cast(current));
            }
            current = current.getParentNode().orElse(null);
        }
        return Optional.empty();
    }

    private String qualifiedTypeName(String packageName, String typeName) {
        return packageName.isBlank() ? typeName : packageName + "." + typeName;
    }

    private String importTarget(ImportDeclaration importDeclaration) {
        String suffix = importDeclaration.isAsterisk() ? ".*" : "";
        return importDeclaration.getNameAsString() + suffix;
    }

    private String methodCallTarget(MethodCallExpr methodCall) {
        return methodCall.getScope()
                .map(scope -> scope + "." + methodCall.getNameAsString())
                .orElseGet(methodCall::getNameAsString);
    }
}
