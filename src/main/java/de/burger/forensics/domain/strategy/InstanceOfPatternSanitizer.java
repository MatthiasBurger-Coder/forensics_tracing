package de.burger.forensics.domain.strategy;

/**
 * Utility for normalizing modern instanceof patterns so that they can be
 * parsed by Byteman. Since Byteman understands only classic "expr instanceof
 * Type" checks, we strip the trailing pattern binding introduced in Java 16
 * (e.g. {@code value instanceof Foo foo}).
 */
final class InstanceOfPatternSanitizer {

    private static final String KEYWORD = "instanceof";

    private InstanceOfPatternSanitizer() {
    }

    static String sanitize(String expression) {
        if (expression == null || expression.indexOf(KEYWORD) < 0) {
            return expression;
        }
        StringBuilder sanitized = new StringBuilder(expression.length());
        int index = 0;
        int length = expression.length();
        while (index < length) {
            int match = expression.indexOf(KEYWORD, index);
            if (match < 0) {
                sanitized.append(expression, index, length);
                break;
            }

            sanitized.append(expression, index, match);
            sanitized.append(KEYWORD);
            int cursor = match + KEYWORD.length();
            cursor = appendWhitespace(expression, sanitized, cursor, length);
            cursor = appendType(expression, sanitized, cursor, length);
            int afterType = skipWhitespace(expression, cursor, length);
            index = skipPatternVariable(expression, afterType, length);
        }
        return sanitized.toString();
    }

    private static int appendWhitespace(String expression, StringBuilder sanitized, int cursor, int length) {
        int next = cursor;
        while (next < length && Character.isWhitespace(expression.charAt(next))) {
            sanitized.append(expression.charAt(next));
            next++;
        }
        return next;
    }

    private static int appendType(String expression, StringBuilder sanitized, int cursor, int length) {
        int next = cursor;
        int genericDepth = 0;
        while (next < length && isTypeCharacter(expression.charAt(next), genericDepth)) {
            char current = expression.charAt(next);
            genericDepth = updateGenericDepth(current, genericDepth);
            sanitized.append(current);
            next++;
        }
        return next;
    }

    private static boolean isTypeCharacter(char current, int genericDepth) {
        return !Character.isWhitespace(current) || genericDepth > 0;
    }

    private static int updateGenericDepth(char current, int genericDepth) {
        if (current == '<') {
            return genericDepth + 1;
        }
        if (current == '>' && genericDepth > 0) {
            return genericDepth - 1;
        }
        return genericDepth;
    }

    private static int skipWhitespace(String expression, int cursor, int length) {
        int next = cursor;
        while (next < length && Character.isWhitespace(expression.charAt(next))) {
            next++;
        }
        return next;
    }

    private static int skipPatternVariable(String expression, int cursor, int length) {
        if (cursor < length && Character.isJavaIdentifierStart(expression.charAt(cursor))) {
            int next = cursor + 1;
            while (next < length && Character.isJavaIdentifierPart(expression.charAt(next))) {
                next++;
            }
            return next;
        }
        return cursor;
    }
}
