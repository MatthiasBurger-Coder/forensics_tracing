package de.burger.forensics.domain.model.cache;

import java.util.Objects;

/**
 * Dependency that can affect a cached scan result.
 */
public record ScanDependency(DependencyKind kind,
                             String sourceRelativePath,
                             String ownerType,
                             String ownerMember,
                             String target,
                             int line,
                             int column) {
    public ScanDependency {
        Objects.requireNonNull(kind, "Dependency kind must not be null.");
        if (sourceRelativePath == null || sourceRelativePath.isBlank()) {
            throw new IllegalArgumentException("Dependency source relative path must not be blank.");
        }
        if (ownerType == null || ownerType.isBlank()) {
            throw new IllegalArgumentException("Dependency owner type must not be blank.");
        }
        Objects.requireNonNull(ownerMember, "Dependency owner member must not be null.");
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Dependency target must not be blank.");
        }
        if (line < 0) {
            throw new IllegalArgumentException("Dependency line must not be negative.");
        }
        if (column < 0) {
            throw new IllegalArgumentException("Dependency column must not be negative.");
        }
    }
}
