package de.burger.forensics.plugin.engine;

import static de.burger.forensics.plugin.engine.JavaPrefilter.prefilterJava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaRegexParser implements JavaScanner {
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:@[\\w$.]+(?:\\([^)]*+\\))?\\s*)*(?:(?:\\b(?:public|protected|private|abstract|final|static|strictfp|sealed)\\b|non-sealed)\\s+)*class\\s+([A-Za-z0-9_]+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:@[\\w$.]+(?:\\([^)]*+\\))?\\s*)*(?:\\b(?:public|protected|private|abstract|final|static|strictfp|synchronized|native|default)\\b\\s+)*(?:<[^>]*+>\\s*)?[\\w$<>\\[\\],.?\\s]+\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*+)\\)\\s*\\{");
    private static final Pattern IF_PATTERN = Pattern.compile("\\bif\\s*\\(([^)]*+)\\)");
    private static final Pattern SWITCH_PATTERN = Pattern.compile("\\bswitch\\s*\\(([^)]*+)\\)");
    private static final Pattern CASE_PATTERN = Pattern.compile("(?m)^[\\t ]*(case\\s+[^:]*+|default)\\s*:");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    @Override
    public List<String> scan(
            String text,
            String helperFqn,
            String packagePrefix,
            boolean includeEntryExit,
            int maxStringLength) {
        List<String> rules = new ArrayList<>();
        String sanitized = prefilterJava(text);
        LineIndex lineIndex = new LineIndex(text);

        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(sanitized);
        String pkg = pkgMatcher.find() ? pkgMatcher.group(1) : "";
        if (packagePrefix != null
                && !packagePrefix.isBlank()
                && !pkg.isBlank()
                && !pkg.startsWith(packagePrefix)) {
            return Collections.emptyList();
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(sanitized);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String fqcn = pkg.isBlank() ? className : pkg + "." + className;
            int openIndex = sanitized.indexOf('{', classMatcher.end());
            if (openIndex < 0) {
                continue;
            }
            int closeIndex = findMatchingBrace(sanitized, openIndex);
            String bodySanitized = sanitized.substring(openIndex + 1, closeIndex);
            Matcher methodMatcher = METHOD_PATTERN.matcher(bodySanitized);
            while (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);
                int methodStartInClass = openIndex + 1 + methodMatcher.start();
                int methodOpen = sanitized.indexOf('{', methodStartInClass);
                if (methodOpen < 0) {
                    continue;
                }
                int methodClose = findMatchingBrace(sanitized, methodOpen);
                String methodBodySanitized = sanitized.substring(methodOpen + 1, methodClose);
                String methodBodyOriginal = text.substring(methodOpen + 1, methodClose);

                if (includeEntryExit) {
                    rules.add(buildEntryRule(helperFqn, fqcn, methodName));
                    rules.add(buildExitRule(helperFqn, fqcn, methodName));
                }

                Matcher ifMatcher = IF_PATTERN.matcher(methodBodySanitized);
                while (ifMatcher.find()) {
                    if (ifMatcher.group(1) == null) {
                        continue;
                    }
                    int offset = methodOpen + 1 + ifMatcher.start();
                    int line = lineIndex.lineAt(offset);
                    int condStart = ifMatcher.start(1);
                    int condEnd = ifMatcher.end(1);
                    String condRaw = methodBodyOriginal.substring(condStart, condEnd);
                    String cond = escape(condRaw, maxStringLength);
                    rules.add(
                            "RULE " + fqcn + "." + methodName + ":" + line + ":if-true\n"
                                    + "CLASS " + fqcn + "\n"
                                    + "METHOD " + methodName + "(..)\n"
                                    + "HELPER " + helperFqn + "\n"
                                    + "AT LINE " + line + "\n"
                                    + "IF (" + condRaw + ")\n"
                                    + "DO iff(\"" + fqcn + "\",\"" + methodName + "\"," + line + ",\""
                                    + cond + "\", true)\n"
                                    + "ENDRULE");
                    rules.add(
                            "RULE " + fqcn + "." + methodName + ":" + line + ":if-false\n"
                                    + "CLASS " + fqcn + "\n"
                                    + "METHOD " + methodName + "(..)\n"
                                    + "HELPER " + helperFqn + "\n"
                                    + "AT LINE " + line + "\n"
                                    + "IF (!(" + condRaw + "))\n"
                                    + "DO iff(\"" + fqcn + "\",\"" + methodName + "\"," + line + ",\""
                                    + cond + "\", false)\n"
                                    + "ENDRULE");
                }

                Matcher switchMatcher = SWITCH_PATTERN.matcher(methodBodySanitized);
                while (switchMatcher.find()) {
                    if (switchMatcher.group(1) == null) {
                        continue;
                    }
                    int offset = methodOpen + 1 + switchMatcher.start();
                    int line = lineIndex.lineAt(offset);
                    int selectorStart = switchMatcher.start(1);
                    int selectorEnd = switchMatcher.end(1);
                    String selectorRaw = methodBodyOriginal.substring(selectorStart, selectorEnd);
                    String sel = escape(selectorRaw, maxStringLength);
                    rules.add(
                            "RULE " + fqcn + "." + methodName + ":" + line + ":when\n"
                                    + "CLASS " + fqcn + "\n"
                                    + "METHOD " + methodName + "(..)\n"
                                    + "HELPER " + helperFqn + "\n"
                                    + "AT LINE " + line + "\n"
                                    + "DO sw(\"" + fqcn + "\",\"" + methodName + "\"," + line + ",\"" + sel
                                    + "\")\n"
                                    + "ENDRULE");
                }

                Matcher caseMatcher = CASE_PATTERN.matcher(methodBodySanitized);
                while (caseMatcher.find()) {
                    if (caseMatcher.group(1) == null) {
                        continue;
                    }
                    int labelStart = caseMatcher.start(1);
                    int labelEnd = caseMatcher.end(1);
                    String labelOriginal = methodBodyOriginal.substring(labelStart, labelEnd);
                    String label = WHITESPACE_PATTERN.matcher(labelOriginal).replaceAll(" ").trim();
                    int offset = methodOpen + 1 + caseMatcher.start();
                    int line = lineIndex.lineAt(offset);
                    String esc = escape(label, maxStringLength);
                    rules.add(
                            "RULE " + fqcn + "." + methodName + ":" + line + ":case\n"
                                    + "CLASS " + fqcn + "\n"
                                    + "METHOD " + methodName + "(..)\n"
                                    + "HELPER " + helperFqn + "\n"
                                    + "AT LINE " + line + "\n"
                                    + "DO kase(\"" + fqcn + "\",\"" + methodName + "\"," + line + ",\"" + esc
                                    + "\")\n"
                                    + "ENDRULE");
                }
            }
        }

        return rules;
    }

    private String escape(String value, int limit) {
        String truncated = value;
        if (limit > 0 && value.length() > limit) {
            truncated = value.substring(0, limit) + "…";
        }
        return truncated
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String buildEntryRule(String helper, String className, String methodName) {
        return "RULE enter@" + className + "." + methodName + "\n"
                + "CLASS " + className + "\n"
                + "METHOD " + methodName + "(..)\n"
                + "HELPER " + helper + "\n"
                + "AT ENTRY\n"
                + "DO enter(\"" + className + "\",\"" + methodName + "\", $LINE)\n"
                + "ENDRULE";
    }

    private String buildExitRule(String helper, String className, String methodName) {
        return "RULE exit@" + className + "." + methodName + "\n"
                + "CLASS " + className + "\n"
                + "METHOD " + methodName + "(..)\n"
                + "HELPER " + helper + "\n"
                + "AT EXIT\n"
                + "DO exit(\"" + className + "\",\"" + methodName + "\", $LINE)\n"
                + "ENDRULE";
    }

    private int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return text.length() - 1;
    }

    private static final class LineIndex {
        private final int[] starts;

        LineIndex(String text) {
            List<Integer> tmp = new ArrayList<>();
            tmp.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    tmp.add(i + 1);
                }
            }
            starts = new int[tmp.size()];
            for (int i = 0; i < tmp.size(); i++) {
                starts[i] = tmp.get(i);
            }
        }

        int lineAt(int offset) {
            if (starts.length == 0) {
                return 1;
            }
            int low = 0;
            int high = starts.length - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int s = starts[mid];
                int next = mid + 1 < starts.length ? starts[mid + 1] : Integer.MAX_VALUE;
                if (offset < s) {
                    high = mid - 1;
                } else if (offset >= next) {
                    low = mid + 1;
                } else {
                    return mid + 1;
                }
            }
            return starts.length;
        }
    }
}
