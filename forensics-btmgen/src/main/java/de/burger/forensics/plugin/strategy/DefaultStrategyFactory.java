package de.burger.forensics.plugin.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal heuristics to recognize a few safe forms; else pass-through. */
public final class DefaultStrategyFactory implements StrategyFactory {

    // Very conservative patterns (no function calls, simple identifiers).
    private static final Pattern EQ_LITERAL = Pattern.compile(
        "^\\s*([a-zA-Z_][\\w\\.$]*)\\s*==\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|[A-Z_][A-Z0-9_]*|[-]?[0-9]+)\\s*$"
    );
    private static final Pattern INSTANCE_OF = Pattern.compile(
        "^\\s*([a-zA-Z_][\\w\\.$]*)\\s+instanceof\\s+([a-zA-Z_][\\w\\.$]*)\\s*$"
    );

    @Override
    public ConditionStrategy from(String conditionText) {
        if (conditionText == null || conditionText.isBlank()) {
            return new OriginalExpressionStrategy("true");
        }
        String raw = conditionText;
        String trimmed = raw.trim();
        String normalized = stripEnclosingParentheses(trimmed);

        SplitResult sr = splitTopLevel(trimmed);
        if (sr != null) {
            List<ConditionStrategy> kids = new ArrayList<>();
            for (String part : sr.parts()) {
                kids.add(from(part));
            }
            boolean hasOriginalChild = kids.stream().anyMatch(child -> child instanceof OriginalExpressionStrategy);
            if (hasOriginalChild) {
                return new OriginalExpressionStrategy(raw);
            }
            return new BooleanCompositeStrategy(sr.op(), kids);
        }

        Matcher mEq = EQ_LITERAL.matcher(normalized);
        if (mEq.matches()) {
            return new EqualsLiteralStrategy(mEq.group(1), mEq.group(2));
        }
        Matcher mIo = INSTANCE_OF.matcher(normalized);
        if (mIo.matches()) {
            return new InstanceOfStrategy(mIo.group(1), mIo.group(2));
        }
        return new OriginalExpressionStrategy(raw);
    }

    private static SplitResult splitTopLevel(String s) {
        int depth = 0;
        List<Integer> andIdx = new ArrayList<>();
        List<Integer> orIdx = new ArrayList<>();
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0) {
                char n = s.charAt(i + 1);
                if (c == '&' && n == '&') {
                    andIdx.add(i);
                }
                if (c == '|' && n == '|') {
                    orIdx.add(i);
                }
            }
        }
        if (!andIdx.isEmpty() || !orIdx.isEmpty()) {
            boolean useAnd = !andIdx.isEmpty();
            List<Integer> idxs = useAnd ? andIdx : orIdx;
            BooleanCompositeStrategy.Op op = useAnd ? BooleanCompositeStrategy.Op.AND : BooleanCompositeStrategy.Op.OR;
            List<String> parts = new ArrayList<>();
            int last = 0;
            for (int idx : idxs) {
                parts.add(s.substring(last, idx).trim());
                last = idx + 2;
            }
            parts.add(s.substring(last).trim());
            return new SplitResult(op, parts);
        }
        return null;
    }

    private static String stripEnclosingParentheses(String input) {
        String current = input;
        while (current.startsWith("(") && current.endsWith(")") && current.length() >= 2) {
            int depth = 0;
            boolean balanced = true;
            for (int i = 0; i < current.length(); i++) {
                char c = current.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth < 0) {
                        balanced = false;
                        break;
                    }
                    if (depth == 0 && i < current.length() - 1) {
                        balanced = false;
                        break;
                    }
                }
            }
            if (!balanced || depth != 0) {
                break;
            }
            current = current.substring(1, current.length() - 1).trim();
        }
        return current.isEmpty() ? input.trim() : current;
    }

    private record SplitResult(BooleanCompositeStrategy.Op op, List<String> parts) {}
}
