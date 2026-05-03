package de.burger.forensics.adaptersupport.javaparser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Structured import information extracted from a Java source file.
 */
public record ImportTable(Map<String, String> explicitTypeImports,
                          Set<String> wildcardTypeImports,
                          Map<String, String> explicitStaticMemberImports,
                          Set<String> wildcardStaticImports) {

    private static final ImportTable EMPTY = new ImportTable(Map.of(), Set.of(), Map.of(), Set.of());

    public ImportTable {
        explicitTypeImports = immutableMap(explicitTypeImports);
        wildcardTypeImports = immutableSet(wildcardTypeImports);
        explicitStaticMemberImports = immutableMap(explicitStaticMemberImports);
        wildcardStaticImports = immutableSet(wildcardStaticImports);
    }

    public static ImportTable empty() {
        return EMPTY;
    }

    public String explicitTypeImport(String identifier) {
        return explicitTypeImports.get(identifier);
    }

    public String explicitStaticMemberImport(String identifier) {
        return explicitStaticMemberImports.get(identifier);
    }

    public boolean hasAmbiguousWildcardTypeCandidate(String identifier) {
        return !explicitTypeImports.containsKey(identifier) && wildcardTypeImports.size() > 1;
    }

    public boolean hasAmbiguousWildcardStaticCandidate(String identifier) {
        return !explicitStaticMemberImports.containsKey(identifier) && wildcardStaticImports.size() > 1;
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
    }

    private static Set<String> immutableSet(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(values, "values")));
    }
}
