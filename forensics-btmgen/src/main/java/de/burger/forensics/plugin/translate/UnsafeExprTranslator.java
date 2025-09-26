package de.burger.forensics.plugin.translate;

/**
 * Translates a restricted subset of raw expressions into SafeEval helper calls.
 * Only supports: '==', '!=', 'instanceof', '&&', '||' with simple identifiers or literals.
 * Falls back to "true" for anything else, ensuring the generated evaluator stays safe.
 */
public final class UnsafeExprTranslator {
    private static final String HELPER = "org.example.trace.SafeEval";

    private UnsafeExprTranslator() {
    }

    public static String toHelperExpr(String raw) {
        if (raw == null || raw.isBlank()) {
            return "true";
        }
        Parser parser = new Parser(raw);
        String result = parser.parseExpression();
        if (!parser.atEnd() || parser.failed()) {
            return "true";
        }
        if (result == null || result.isBlank()) {
            return "true";
        }
        if (!result.contains(HELPER + ".")) {
            return "true";
        }
        return result;
    }

    private static final class Parser {
        private final String input;
        private int pos;
        private boolean failed;

        private Parser(String input) {
            this.input = input;
            this.pos = 0;
            this.failed = false;
        }

        private boolean failed() {
            return failed;
        }

        private boolean atEnd() {
            skipWhitespace();
            return pos >= input.length();
        }

        private String parseExpression() {
            String expr = parseOr();
            if (failed) {
                return null;
            }
            return expr;
        }

        private String parseOr() {
            String left = parseAnd();
            if (failed) {
                return null;
            }
            while (true) {
                int checkpoint = pos;
                if (!match("||")) {
                    pos = checkpoint;
                    break;
                }
                String right = parseAnd();
                if (failed || right == null) {
                    failed = true;
                    return null;
                }
                if (left == null) {
                    failed = true;
                    return null;
                }
                left = HELPER + ".or(" + left + ", " + right + ")";
            }
            return left;
        }

        private String parseAnd() {
            String left = parseComparison();
            if (failed) {
                return null;
            }
            while (true) {
                int checkpoint = pos;
                if (!match("&&")) {
                    pos = checkpoint;
                    break;
                }
                String right = parseComparison();
                if (failed || right == null) {
                    failed = true;
                    return null;
                }
                if (left == null) {
                    failed = true;
                    return null;
                }
                left = HELPER + ".and(" + left + ", " + right + ")";
            }
            return left;
        }

        private String parseComparison() {
            String left = parsePrimary();
            if (failed) {
                return null;
            }
            skipWhitespace();
            if (match("instanceof")) {
                skipWhitespace();
                String type = parseType();
                if (failed || type == null) {
                    failed = true;
                    return null;
                }
                return HELPER + ".ifInstanceOf(" + left + ", \"" + type + "\")";
            }
            if (match("==")) {
                skipWhitespace();
                String right = parseValue();
                if (failed || right == null) {
                    failed = true;
                    return null;
                }
                return HELPER + ".ifEq(" + left + ", " + right + ")";
            }
            if (match("!=")) {
                skipWhitespace();
                String right = parseValue();
                if (failed || right == null) {
                    failed = true;
                    return null;
                }
                return "!" + HELPER + ".ifEq(" + left + ", " + right + ")";
            }
            return left;
        }

        private String parsePrimary() {
            skipWhitespace();
            if (peek() == '(') {
                pos++;
                String inner = parseOr();
                if (failed) {
                    return null;
                }
                skipWhitespace();
                if (peek() != ')') {
                    failed = true;
                    return null;
                }
                pos++;
                return inner;
            }
            return parseValue();
        }

        private String parseValue() {
            skipWhitespace();
            if (pos >= input.length()) {
                failed = true;
                return null;
            }
            char c = input.charAt(pos);
            if (c == '"' || c == '\'') {
                return parseQuotedLiteral();
            }
            if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                return parseIdentifier();
            }
            failed = true;
            return null;
        }

        private String parseQuotedLiteral() {
            char quote = input.charAt(pos);
            int start = pos;
            pos++;
            boolean escaped = false;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (ch == quote && !escaped) {
                    pos++;
                    return input.substring(start, pos);
                }
                if (ch == '\\' && !escaped) {
                    escaped = true;
                } else {
                    escaped = false;
                }
                pos++;
            }
            failed = true;
            return null;
        }

        private String parseNumber() {
            int start = pos;
            if (input.charAt(pos) == '-') {
                pos++;
            }
            boolean hasDigits = false;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (Character.isDigit(ch)) {
                    hasDigits = true;
                    pos++;
                } else if (ch == '.') {
                    pos++;
                } else {
                    break;
                }
            }
            if (!hasDigits) {
                failed = true;
                return null;
            }
            return input.substring(start, pos);
        }

        private String parseIdentifier() {
            int start = pos;
            pos++;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '.') {
                    pos++;
                } else {
                    break;
                }
            }
            String ident = input.substring(start, pos).trim();
            int lookahead = pos;
            while (lookahead < input.length() && Character.isWhitespace(input.charAt(lookahead))) {
                lookahead++;
            }
            if (lookahead < input.length()) {
                char next = input.charAt(lookahead);
                if (next == '(' || next == '[') {
                    failed = true;
                    return null;
                }
            }
            return ident;
        }

        private String parseType() {
            int start = pos;
            if (start >= input.length()) {
                failed = true;
                return null;
            }
            char first = input.charAt(pos);
            if (!(Character.isLetter(first) || first == '_' || first == '$')) {
                failed = true;
                return null;
            }
            pos++;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '.') {
                    pos++;
                } else {
                    break;
                }
            }
            return input.substring(start, pos).trim();
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private boolean match(String token) {
            skipWhitespace();
            if (input.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }

        private char peek() {
            if (pos >= input.length()) {
                return '\0';
            }
            return input.charAt(pos);
        }
    }
}
