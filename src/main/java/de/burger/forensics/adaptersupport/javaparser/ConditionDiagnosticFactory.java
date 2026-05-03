package de.burger.forensics.adaptersupport.javaparser;

import de.burger.forensics.domain.model.ConditionDiagnostic;
import de.burger.forensics.domain.model.ConditionResolutionStatus;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceContext;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.validation.UnresolvedTypeReferenceValidator;

import java.util.List;
import java.util.Objects;

/**
 * Creates condition diagnostics while the scanner still has source context.
 */
public final class ConditionDiagnosticFactory {

    private static final String AMBIGUOUS_REASON =
            "Multiple wildcard import candidates keep the source-level type reference ambiguous.";
    private static final String UNRESOLVED_REASON =
            "No deterministic type qualification was available during condition rendering.";

    private final UnresolvedTypeReferenceValidator validator;

    public ConditionDiagnosticFactory() {
        this(new UnresolvedTypeReferenceValidator());
    }

    ConditionDiagnosticFactory(UnresolvedTypeReferenceValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    List<ConditionDiagnostic> diagnostics(String expression, SourceLocation location, MethodScanContext context) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        SourceContext sourceContext = sourceContext(context);
        ScanEvent event = new ScanEvent(location, context.methodSignature(), RuleTemplate.IF_TRUE, expression, "java", "");
        return validator.validate(event).issues().stream()
                .map(issue -> diagnostic(issue.symbol(), issue.expressionPreview(), location, sourceContext, context))
                .toList();
    }

    private static ConditionDiagnostic diagnostic(String symbol,
                                                  String expressionPreview,
                                                  SourceLocation location,
                                                  SourceContext sourceContext,
                                                  MethodScanContext context) {
        ConditionResolutionStatus status = resolutionStatus(symbol, context);
        return new ConditionDiagnostic(
                symbol,
                expressionPreview,
                status,
                reason(status),
                location,
                sourceContext);
    }

    private static ConditionResolutionStatus resolutionStatus(String symbol, MethodScanContext context) {
        if (context.hasAmbiguousWildcardTypeCandidate(symbol)
                || context.hasAmbiguousWildcardStaticCandidate(symbol)) {
            return ConditionResolutionStatus.AMBIGUOUS;
        }
        return ConditionResolutionStatus.UNRESOLVED;
    }

    private static String reason(ConditionResolutionStatus status) {
        return status == ConditionResolutionStatus.AMBIGUOUS ? AMBIGUOUS_REASON : UNRESOLVED_REASON;
    }

    private static SourceContext sourceContext(MethodScanContext context) {
        return new SourceContext(
                context.packageName(),
                context.sourceFilePath(),
                context.fullyQualifiedSourceTypeName(),
                context.simpleClassName(),
                context.methodName(),
                context.methodSignature());
    }
}
