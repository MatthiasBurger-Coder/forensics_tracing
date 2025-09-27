package de.burger.forensics.plugin.engine;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaPrefilter {
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//.*?$", Pattern.MULTILINE);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern CHAR_LITERAL = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'");

    private JavaPrefilter() {}

    public static String prefilterJava(String source) {
        String withoutStrings = replaceAll(STRING_LITERAL, source, s -> replaceLiteralPreservingLength(s, '"'));
        String withoutChars = replaceAll(CHAR_LITERAL, withoutStrings, s -> replaceLiteralPreservingLength(s, '\''));
        String withoutBlock = replaceAll(BLOCK_COMMENT, withoutChars, JavaPrefilter::blankWithNewlines);
        return replaceAll(LINE_COMMENT, withoutBlock, JavaPrefilter::blankWithNewlines);
    }

    private static String replaceAll(Pattern pattern, String input, Function<String, String> replacer) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = Matcher.quoteReplacement(replacer.apply(m.group()));
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String blankWithNewlines(String segment) {
        StringBuilder sb = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            char ch = segment.charAt(i);
            sb.append(ch == '\n' ? '\n' : ' ');
        }
        return sb.toString();
    }

    private static String replaceLiteralPreservingLength(String literal, char delimiter) {
        if (literal.length() <= 2) {
            return new String(new char[]{delimiter, delimiter});
        }
        return delimiter +
                " ".repeat(literal.length() - 2) +
                delimiter;
    }
}
