package de.burger.forensics.domain.validation;

import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.RuleTemplate;

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
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (inString) {
                out.append(' ');
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(' ');
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                out.append(' ');
            } else if (current == '\'') {
                inChar = true;
                out.append(' ');
            } else {
                out.append(current);
            }
        }
        return out.toString();
    }
}
