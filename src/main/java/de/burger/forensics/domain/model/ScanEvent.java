package de.burger.forensics.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Domain event emitted by the scanning port.
 */
public record ScanEvent(SourceLocation location,
                        String signature,
                        RuleTemplate kind,
                        String conditionText,
                        String language,
                        String returnType,
                        List<ConditionDiagnostic> conditionDiagnostics) {

    public ScanEvent(SourceLocation location,
                     String signature,
                     RuleTemplate kind,
                     String conditionText,
                     String language,
                     String returnType) {
        this(location, signature, kind, conditionText, language, returnType, List.of());
    }

    public ScanEvent {
        conditionDiagnostics = List.copyOf(
                Objects.requireNonNull(conditionDiagnostics, "conditionDiagnostics"));
    }
}
