package de.burger.forensics.application.service;

import de.burger.forensics.application.AnalysisContext;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.entry.WarningEntry;
import de.burger.forensics.domain.port.out.LogPort;
import de.burger.forensics.domain.validation.ConditionValidationReport;
import de.burger.forensics.domain.validation.UnresolvedTypeReferenceValidator;

import java.util.List;

final class ConditionValidationSupport {

    private static final UnresolvedTypeReferenceValidator VALIDATOR = new UnresolvedTypeReferenceValidator();

    private ConditionValidationSupport() {
    }

    static ConditionValidationReport validate(GenerationRequest request,
                                              AnalysisContext context,
                                              LogPort log,
                                              List<ScanEvent> events) {
        ConditionValidationReport report = VALIDATOR.validate(events);
        report.issues().forEach(issue ->
                context.addWarning(new WarningEntry(issue.message(), "condition-validation")));
        if (report.hasIssues()) {
            log.warn(report.summaryMessage("scan profile report"));
        }
        if (request.strictConditionValidation() && report.hasIssues()) {
            throw new ConditionValidationException(report);
        }
        return report;
    }
}
