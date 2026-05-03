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
                                Map<String, String> typeImports,
                                Map<String, String> staticMemberImports,
                                Set<String> wildcardTypeImports,
                                Set<String> wildcardStaticImports,
                                String packageName,
                                String sourceTypeName) {

    public MethodScanContext(MethodDeclaration declaration,
                             Map<String, Integer> parameterIndexes,
                             Set<String> localVariables) {
        this(declaration, parameterIndexes, localVariables, Map.of(), Map.of(), Set.of(), Set.of(), "", "");
    }

    public MethodScanContext(MethodDeclaration declaration,
                             Map<String, Integer> parameterIndexes,
                             Set<String> localVariables,
                             Map<String, String> typeImports,
                             Map<String, String> staticMemberImports) {
        this(declaration, parameterIndexes, localVariables, typeImports, staticMemberImports, Set.of(), Set.of(), "", "");
    }

    public MethodScanContext(MethodDeclaration declaration,
                             Map<String, Integer> parameterIndexes,
                             Set<String> localVariables,
                             Map<String, String> typeImports,
                             Map<String, String> staticMemberImports,
                             Set<String> wildcardTypeImports,
                             Set<String> wildcardStaticImports,
                             String packageName,
                             String sourceTypeName) {
        this.declaration = Objects.requireNonNull(declaration, "declaration");
        this.parameterIndexes = Map.copyOf(parameterIndexes);
        this.localVariables = Set.copyOf(localVariables);
        this.typeImports = Map.copyOf(typeImports);
        this.staticMemberImports = Map.copyOf(staticMemberImports);
        this.wildcardTypeImports = Set.copyOf(wildcardTypeImports);
        this.wildcardStaticImports = Set.copyOf(wildcardStaticImports);
        this.packageName = Objects.requireNonNullElse(packageName, "");
        this.sourceTypeName = Objects.requireNonNullElse(sourceTypeName, "");
    }

    public Integer parameterIndex(String name) {
        return parameterIndexes.get(name);
    }

    public boolean isLocalVariable(String identifier) {
        return localVariables.contains(identifier);
    }

    public String typeImport(String identifier) {
        return typeImports.get(identifier);
    }

    public String staticMemberImport(String identifier) {
        return staticMemberImports.get(identifier);
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
        return !typeImports.containsKey(identifier) && wildcardTypeImports.size() > 1;
    }

    public boolean hasAmbiguousWildcardStaticCandidate(String identifier) {
        return !staticMemberImports.containsKey(identifier) && wildcardStaticImports.size() > 1;
    }
}
