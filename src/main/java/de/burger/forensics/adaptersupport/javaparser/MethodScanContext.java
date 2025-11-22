package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contextual information shared across rendering operations for a method.
 */
public record MethodScanContext(MethodDeclaration declaration, Map<String, Integer> parameterIndexes,
                                Set<String> localVariables) {

    public MethodScanContext(MethodDeclaration declaration,
                             Map<String, Integer> parameterIndexes,
                             Set<String> localVariables) {
        this.declaration = Objects.requireNonNull(declaration, "declaration");
        this.parameterIndexes = Map.copyOf(parameterIndexes);
        this.localVariables = Set.copyOf(localVariables);
    }

    public Integer parameterIndex(String name) {
        return parameterIndexes.get(name);
    }

    public boolean isLocalVariable(String identifier) {
        return localVariables.contains(identifier);
    }
}
