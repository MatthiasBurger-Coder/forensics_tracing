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

            // Preserve whitespace between keyword and type declaration
            while (cursor < length && Character.isWhitespace(expression.charAt(cursor))) {
                sanitized.append(expression.charAt(cursor));
                cursor++;
            }

            // Copy the full type portion, including generics or array suffixes.
            int genericDepth = 0;
            while (cursor < length) {
                char c = expression.charAt(cursor);
                if (c == '<') {
                    genericDepth++;
                } else if (c == '>') {
                    if (genericDepth > 0) {
                        genericDepth--;
                    }
                } else if (Character.isWhitespace(c) && genericDepth == 0) {
                    break;
                }
                sanitized.append(c);
                cursor++;
            }

            // Skip whitespace between the type and the pattern variable name.
            int afterType = cursor;
            while (afterType < length && Character.isWhitespace(expression.charAt(afterType))) {
                afterType++;
            }

            // Drop the pattern variable (e.g. "foo" in "instanceof Bar foo").
            if (afterType < length && Character.isJavaIdentifierStart(expression.charAt(afterType))) {
                cursor = afterType + 1;
                while (cursor < length && Character.isJavaIdentifierPart(expression.charAt(cursor))) {
                    cursor++;
                }
                index = cursor;
            } else {
                index = afterType;
            }
        }
        return sanitized.toString();
    }
}
