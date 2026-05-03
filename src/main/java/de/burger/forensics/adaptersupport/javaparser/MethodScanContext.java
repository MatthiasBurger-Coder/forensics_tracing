package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contextual information shared across rendering operations for a method.
 */
public record MethodScanContext(MethodDeclaration declaration, Map<String, Integer> parameterIndexes,
                                Set<String> localVariables,
                                ImportTable importTable,
                                String packageName,
                                String sourceFilePath,
                                String sourceTypeName,
                                String simpleClassName,
                                String methodName,
                                String methodSignature) {

    public MethodScanContext(MethodDeclaration declaration,
                             Map<String, Integer> parameterIndexes,
                             Set<String> localVariables) {
        this(
                declaration,
                parameterIndexes,
                localVariables,
                ImportTable.empty(),
                "",
                "",
                "",
                "",
                "",
                "");
    }

    public MethodScanContext(MethodDeclaration declaration,
                             Map<String, Integer> parameterIndexes,
                             Set<String> localVariables,
                             Map<String, String> typeImports,
                             Map<String, String> staticMemberImports) {
        this(
                declaration,
                parameterIndexes,
                localVariables,
                new ImportTable(typeImports, Set.of(), staticMemberImports, Set.of()),
                "",
                "",
                "",
                "",
                "",
                "");
    }

    public MethodScanContext(MethodDeclaration declaration,
                             Map<String, Integer> parameterIndexes,
                             Set<String> localVariables,
                             Map<String, String> typeImports,
                             Map<String, String> staticMemberImports,
                             Set<String> wildcardTypeImports,
                             Set<String> wildcardStaticImports,
                             String packageName,
                             String sourceFilePath,
                             String sourceTypeName,
                             String simpleClassName,
                             String methodName,
                             String methodSignature) {
        this(
                declaration,
                parameterIndexes,
                localVariables,
                new ImportTable(typeImports, wildcardTypeImports, staticMemberImports, wildcardStaticImports),
                packageName,
                sourceFilePath,
                sourceTypeName,
                simpleClassName,
                methodName,
                methodSignature);
    }

    public MethodScanContext(MethodDeclaration declaration,
                             Map<String, Integer> parameterIndexes,
                             Set<String> localVariables,
                             ImportTable importTable,
                             String packageName,
                             String sourceFilePath,
                             String sourceTypeName,
                             String simpleClassName,
                             String methodName,
                             String methodSignature) {
        this.declaration = Objects.requireNonNull(declaration, "declaration");
        this.parameterIndexes = Map.copyOf(parameterIndexes);
        this.localVariables = Set.copyOf(localVariables);
        this.importTable = Objects.requireNonNull(importTable, "importTable");
        this.packageName = Objects.requireNonNullElse(packageName, "");
        this.sourceFilePath = Objects.requireNonNullElse(sourceFilePath, "");
        this.sourceTypeName = Objects.requireNonNullElse(sourceTypeName, "");
        this.simpleClassName = Objects.requireNonNullElse(simpleClassName, "");
        this.methodName = Objects.requireNonNullElse(methodName, "");
        this.methodSignature = Objects.requireNonNullElse(methodSignature, "");
    }

    public Integer parameterIndex(String name) {
        return parameterIndexes.get(name);
    }

    public boolean isLocalVariable(String identifier) {
        return localVariables.contains(identifier);
    }

    public String typeImport(String identifier) {
        return importTable.explicitTypeImport(identifier);
    }

    public String staticMemberImport(String identifier) {
        return importTable.explicitStaticMemberImport(identifier);
    }

    public Map<String, String> typeImports() {
        return importTable.explicitTypeImports();
    }

    public Map<String, String> staticMemberImports() {
        return importTable.explicitStaticMemberImports();
    }

    public Set<String> wildcardTypeImports() {
        return importTable.wildcardTypeImports();
    }

    public Set<String> wildcardStaticImports() {
        return importTable.wildcardStaticImports();
    }

    public String fullyQualifiedSourceTypeName() {
        if (sourceTypeName.isBlank()) {
            return "";
        }
        return packageName.isBlank() ? sourceTypeName : packageName + "." + sourceTypeName;
    }

    public boolean isCurrentSourceType(String qualifiedTypeName) {
        return !fullyQualifiedSourceTypeName().isBlank() && fullyQualifiedSourceTypeName().equals(qualifiedTypeName);
    }

    public boolean hasAmbiguousWildcardTypeCandidate(String identifier) {
        return importTable.hasAmbiguousWildcardTypeCandidate(identifier);
    }

    public boolean hasAmbiguousWildcardStaticCandidate(String identifier) {
        return importTable.hasAmbiguousWildcardStaticCandidate(identifier);
    }
}
