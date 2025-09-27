package de.burger.forensics.plugin.scan.kotlin;

import de.burger.forensics.plugin.scan.ScanEvent;
import de.burger.forensics.plugin.scan.SourceScanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// English comments only in code.
// Lightweight Kotlin source scanner implemented in Java without Kotlin PSI.
// It approximates detection of control-flow and simple write events using regexes
// and a basic brace-based scope tracker. Good enough for generating scan events
// similar to the previous Kotlin implementation without adding Kotlin dependencies.
public final class KotlinAstScanner implements SourceScanner {

    private static final Pattern PKG_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)");
    private static final Pattern CLASS_PATTERN = Pattern.compile("^\\s*(?:data\\s+)?(?:sealed\\s+)?(?:open\\s+)?(?:internal\\s+|public\\s+|private\\s+|protected\\s+)?(?:class|object)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern FUN_PATTERN = Pattern.compile("^\\s*(?:suspend\\s+)?(?:inline\\s+)?(?:operator\\s+)?(?:tailrec\\s+)?(?:infix\\s+)?(?:internal\\s+|public\\s+|private\\s+|protected\\s+)?fun\\s+([A-Za-z_][A-Za-z0-9_]*?)\\s*\\((.*?)\\)");
    private static final Pattern IF_START_PATTERN = Pattern.compile("\\bif\\s*\\(");
    private static final Pattern WHEN_START_PATTERN = Pattern.compile("\\bwhen\\s*\\(");
    private static final Pattern WHEN_SUBJECTLESS_PATTERN = Pattern.compile("\\bwhen\\s*\\{");
    private static final Pattern WHEN_ENTRY_PATTERN = Pattern.compile("^\\s*(?:else|[^\\r\\n]+?)\\s*->");
    private static final Pattern RETURN_PATTERN = Pattern.compile("(^|\\W)return(\\W|$)");
    private static final Pattern THROW_PATTERN = Pattern.compile("\\bthrow\\s+(.+)");
    private static final Pattern WRITE_PATTERN = Pattern.compile("(?<![<>!=])\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(?![=])");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public List<ScanEvent> scan(Path root, List<String> includePkgs, List<String> excludePkgs) {
        var out = new ArrayList<ScanEvent>();
        if (root == null) return out;
        // Walk filesystem with bounded depth and skip directory symlinks to avoid pathological recursion.
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 64, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".kt")) {
                        scanFile(file, includePkgs, excludePkgs, out);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
        return out;
    }

    private void scanFile(Path path, List<String> includePkgs, List<String> excludePkgs, List<ScanEvent> out) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        String pkg = "";
        for (String line : lines) {
            Matcher m = PKG_PATTERN.matcher(line);
            if (m.find()) {
                pkg = m.group(1);
                break;
            }
        }
        if (!include(pkg, includePkgs) || exclude(pkg, excludePkgs)) {
            return;
        }

        String fileTop = stripExtension(path.getFileName().toString()) + "Kt";
        Deque<String> classStack = new ArrayDeque<>();
        Deque<Integer> braceStack = new ArrayDeque<>();
        String currentFun = null;
        String currentSig = null;
        int funBraceLevel = -1;
        int brace = 0;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = stripLineComments(raw);

            // Track braces for scope
            brace += countChar(line, '{');
            brace -= countChar(line, '}');

            // Enter class/object
            Matcher mc = CLASS_PATTERN.matcher(line);
            if (mc.find()) {
                classStack.push(mc.group(1));
                braceStack.push(brace);
            }
            // Pop class/object when brace drops below stored level
            while (!braceStack.isEmpty() && brace < braceStack.peek()) {
                braceStack.pop();
                classStack.pop();
            }

            // Function start
            if (currentFun == null) {
                Matcher mf = FUN_PATTERN.matcher(line);
                if (mf.find()) {
                    currentFun = mf.group(1);
                    currentSig = buildSignature(mf.group(1), mf.group(2));
                    funBraceLevel = brace; // function body usually starts after this when '{' increases
                }
            }

            String fqcn = buildFqcn(pkg, classStack, fileTop);

            if (currentFun != null) {
                // Within a function body: collect events
                collectLineEvents(lines, i, line, fqcn, currentFun, currentSig, out);
                // Detect function end strictly when brace drops below the starting level.
                // Using equality here can prematurely end the function when closing inner blocks
                // that return to the function's starting depth.
                if (brace < funBraceLevel) {
                    currentFun = null;
                    currentSig = null;
                    funBraceLevel = -1;
                }
            }
        }
    }

    private void collectLineEvents(List<String> lines, int index, String line, String fqcn, String methodName, String signature, List<ScanEvent> out) {
        int lineNo = index + 1;

        // if (...) -> if-true and if-false (approximate: assume potential else)
        Matcher mi = IF_START_PATTERN.matcher(line);
        while (mi.find()) {
            int parenStart = mi.end() - 1;
            String condition = captureParenthesized(lines, index, line, parenStart);
            if (condition != null && !condition.isBlank()) {
                out.add(new ScanEvent("kotlin", fqcn, methodName, signature, "if-true", lineNo, condition));
                out.add(new ScanEvent("kotlin", fqcn, methodName, signature, "if-false", lineNo, condition));
            }
        }
        // when(selector)
        Matcher mw = WHEN_START_PATTERN.matcher(line);
        while (mw.find()) {
            int parenStart = mw.end() - 1;
            String subject = captureParenthesized(lines, index, line, parenStart);
            if (subject != null && !subject.isBlank()) {
                out.add(new ScanEvent("kotlin", fqcn, methodName, signature, "switch", lineNo, subject));
            }
        }
        // subject-less when { ... }
        if (WHEN_SUBJECTLESS_PATTERN.matcher(line).find()) {
            out.add(new ScanEvent("kotlin", fqcn, methodName, signature, "switch", lineNo, null));
        }
        // when branch entries (simple heuristic: lines like `x ->` or `else ->`)
        Matcher mEntry = WHEN_ENTRY_PATTERN.matcher(line);
        if (mEntry.find()) {
            String label = line.trim();
            int arrow = label.indexOf("->");
            if (arrow > 0) {
                label = label.substring(0, arrow).trim();
            }
            out.add(new ScanEvent("kotlin", fqcn, methodName, signature, "when-branch", lineNo, label));
        }
        // return
        if (RETURN_PATTERN.matcher(line).find()) {
            out.add(new ScanEvent("kotlin", fqcn, methodName, signature, "return", lineNo, null));
        }
        // throw
        Matcher mt = THROW_PATTERN.matcher(line);
        if (mt.find()) {
            out.add(new ScanEvent("kotlin", fqcn, methodName, signature, "throw", lineNo, mt.group(1).trim()))
            ;
        }
        // simple assignment writes
        Matcher mwrt = WRITE_PATTERN.matcher(line);
        while (mwrt.find()) {
            String name = mwrt.group(1);
            out.add(new ScanEvent("kotlin", fqcn, methodName, signature, "write", lineNo, name));
        }
    }

    private String captureParenthesized(List<String> lines, int startLine, String currentLine, int openingIndex) {
        int depth = 1;
        StringBuilder buffer = new StringBuilder();
        int lineIndex = startLine;
        String line = currentLine;
        int charIndex = openingIndex + 1;
        boolean needSpace = false;
        while (true) {
            while (charIndex < line.length()) {
                char ch = line.charAt(charIndex);
                if (Character.isWhitespace(ch)) {
                    if (!buffer.isEmpty() && !Character.isWhitespace(buffer.charAt(buffer.length() - 1))) {
                        needSpace = true;
                    }
                    charIndex++;
                    continue;
                }
                if (needSpace && requiresSpaceBefore(buffer, ch)) {
                    buffer.append(' ');
                }
                needSpace = false;
                if (ch == '(') {
                    depth++;
                    buffer.append(ch);
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        return normalizeWhitespace(buffer.toString());
                    }
                    buffer.append(ch);
                } else {
                    buffer.append(ch);
                }
                charIndex++;
            }
            lineIndex++;
            if (lineIndex >= lines.size()) {
                return null;
            }
            line = stripLineComments(lines.get(lineIndex));
            charIndex = 0;
        }
    }

    private String normalizeWhitespace(String input) {
        if (input == null) {
            return null;
        }
        return WHITESPACE.matcher(input).replaceAll(" ").trim();
    }

    private boolean requiresSpaceBefore(StringBuilder buffer, char ch) {
        if (buffer.isEmpty()) {
            return false;
        }
        char last = buffer.charAt(buffer.length() - 1);
        if (last == '(' || last == '[') {
            return false;
        }
        if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '"' || ch == '\'' || ch == '@') {
            return true;
        }
        return !(ch == '.' || ch == ',' || ch == ')' || ch == ']' || ch == ':' || ch == ';');
    }

    private static String buildFqcn(String pkg, Deque<String> classStack, String fileTop) {
        String typeName;
        if (classStack.isEmpty()) {
            typeName = fileTop;
        } else {
            List<String> parts = new ArrayList<>(classStack);
            Collections.reverse(parts);
            typeName = String.join("$", parts);
        }
        return (pkg == null || pkg.isBlank()) ? typeName : pkg + "." + typeName;
    }

    private static String buildSignature(String name, String paramsRaw) {
        if (paramsRaw == null) return name + "()";
        // params like: a: Int, b: String -> we keep only type tokens after ':'
        String[] parts = paramsRaw.split(",");
        var types = new ArrayList<String>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            int idx = t.lastIndexOf(':');
            if (idx >= 0 && idx + 1 < t.length()) {
                t = t.substring(idx + 1).trim();
            } else if (!t.isEmpty()) {
                t = "Any"; // fallback when no explicit type
            } else {
                t = "";
            }
            if (!t.isEmpty()) types.add(t);
        }
        return name + "(" + String.join(",", types) + ")";
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    private static String stripLineComments(String line) {
        // Very naive removal of // comments to avoid false positives
        int idx = line.indexOf("//");
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    private boolean include(String pkg, List<String> includes) {
        return includes == null || includes.isEmpty() || includes.stream().anyMatch(pkg::startsWith);
    }

    private boolean exclude(String pkg, List<String> excludes) {
        return excludes != null && excludes.stream().anyMatch(pkg::startsWith);
    }
}
