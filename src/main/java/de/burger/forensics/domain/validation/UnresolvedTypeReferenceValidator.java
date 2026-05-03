package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.ConditionDiagnostic;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.SourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Flags source-level simple type references that Byteman may not resolve while loading rules.
 */
public final class UnresolvedTypeReferenceValidator {

    private static final Set<RuleTemplate> EXECUTABLE_CONDITION_TEMPLATES = EnumSet.of(
            RuleTemplate.IF_TRUE,
            RuleTemplate.IF_FALSE
    );

    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "Boolean",
            "Byte",
            "Character",
            "Class",
            "Double",
            "Enum",
            "Exception",
            "Float",
            "Integer",
            "Long",
            "Math",
            "Number",
            "Object",
            "RuntimeException",
            "Short",
            "String",
            "StringBuffer",
            "StringBuilder",
            "System",
            "Thread",
            "Throwable"
    );

    public ConditionValidationReport validate(List<ScanEvent> events) {
        Objects.requireNonNull(events, "events");
        List<ConditionValidationIssue> issues = new ArrayList<>();
        for (ScanEvent event : events) {
            validate(event).issues().forEach(issues::add);
        }
        return new ConditionValidationReport(issues);
    }

    public ConditionValidationReport validate(ScanEvent event) {
        Objects.requireNonNull(event, "event");
        if (!EXECUTABLE_CONDITION_TEMPLATES.contains(event.kind())) {
            return ConditionValidationReport.empty();
        }
        if (!event.conditionDiagnostics().isEmpty()) {
            return reportFromDiagnostics(event);
        }
        String expression = event.conditionText();
        if (expression == null || expression.isBlank()) {
            return ConditionValidationReport.empty();
        }
        String sanitized = maskLiterals(expression);
        List<ConditionValidationIssue> issues = new ArrayList<>();
        int cursor = 0;
        while (cursor < sanitized.length()) {
            if (!isIdentifierStart(sanitized.charAt(cursor))) {
                cursor++;
                continue;
            }
            int start = cursor;
            cursor++;
            while (cursor < sanitized.length() && isIdentifierPart(sanitized.charAt(cursor))) {
                cursor++;
            }
            String symbol = sanitized.substring(start, cursor);
            if (isSuspiciousSimpleTypeReference(symbol, sanitized, start, cursor)) {
                issues.add(new ConditionValidationIssue(event.location(), expression, symbol, event.kind()));
            }
        }
        return new ConditionValidationReport(issues);
    }

    private static ConditionValidationReport reportFromDiagnostics(ScanEvent event) {
        List<ConditionValidationIssue> issues = event.conditionDiagnostics().stream()
                .map(diagnostic -> issueFromDiagnostic(event, diagnostic))
                .toList();
        return new ConditionValidationReport(issues);
    }

    private static ConditionValidationIssue issueFromDiagnostic(ScanEvent event, ConditionDiagnostic diagnostic) {
        SourceLocation location = diagnostic.location() == null ? event.location() : diagnostic.location();
        return new ConditionValidationIssue(
                location,
                diagnostic.expressionPreview(),
                diagnostic.symbol(),
                event.kind(),
                diagnostic.resolutionStatus(),
                diagnostic.reason(),
                diagnostic.sourceContext());
    }

    private static boolean isSuspiciousSimpleTypeReference(String symbol, String expression, int start, int end) {
        return Character.isUpperCase(symbol.charAt(0))
                && !symbol.startsWith("$")
                && !JAVA_LANG_TYPES.contains(symbol)
                && previousNonWhitespace(expression, start) != '.'
                && nextNonWhitespace(expression, end) == '.';
    }

    private static char previousNonWhitespace(String expression, int start) {
        int cursor = start - 1;
        while (cursor >= 0 && Character.isWhitespace(expression.charAt(cursor))) {
            cursor--;
        }
        return cursor < 0 ? '\0' : expression.charAt(cursor);
    }

    private static char nextNonWhitespace(String expression, int end) {
        int cursor = end;
        while (cursor < expression.length() && Character.isWhitespace(expression.charAt(cursor))) {
            cursor++;
        }
        return cursor >= expression.length() ? '\0' : expression.charAt(cursor);
    }

    private static boolean isIdentifierStart(char candidate) {
        return Character.isJavaIdentifierStart(candidate);
    }

    private static boolean isIdentifierPart(char candidate) {
        return Character.isJavaIdentifierPart(candidate);
    }

    private static String maskLiterals(String expression) {
        StringBuilder out = new StringBuilder(expression.length());
        LiteralMaskState state = LiteralMaskState.outside();
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            out.append(state.mask(current));
            state = state.next(current);
        }
        return out.toString();
    }

    private enum LiteralType {
        NONE,
        STRING,
        CHARACTER
    }

    private record LiteralMaskState(LiteralType type, boolean escaped) {

        static LiteralMaskState outside() {
            return new LiteralMaskState(LiteralType.NONE, false);
        }

        char mask(char current) {
            return insideLiteral() || opensLiteral(current) ? ' ' : current;
        }

        LiteralMaskState next(char current) {
            if (!insideLiteral()) {
                return openLiteral(current);
            }
            if (escaped) {
                return new LiteralMaskState(type, false);
            }
            if (current == '\\') {
                return new LiteralMaskState(type, true);
            }
            if (closesLiteral(current)) {
                return outside();
            }
            return this;
        }

        private boolean insideLiteral() {
            return type != LiteralType.NONE;
        }

        private static boolean opensLiteral(char current) {
            return current == '"' || current == '\'';
        }

        private static LiteralMaskState openLiteral(char current) {
            if (current == '"') {
                return new LiteralMaskState(LiteralType.STRING, false);
            }
            if (current == '\'') {
                return new LiteralMaskState(LiteralType.CHARACTER, false);
            }
            return outside();
        }

        private boolean closesLiteral(char current) {
            return (type == LiteralType.STRING && current == '"')
                    || (type == LiteralType.CHARACTER && current == '\'');
        }
    }
}
