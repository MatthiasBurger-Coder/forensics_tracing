// DEST: src/main/java/de/burger/forensics/plugin/GlobUtils.java
package de.burger.forensics.plugin;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public final class GlobUtils {

    private static final ConcurrentHashMap<String, Pattern> GLOB_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PathMatcher> PATH_MATCHER_CACHE = new ConcurrentHashMap<>();

    private GlobUtils() {
        throw new AssertionError("No instances.");
    }

    @NotNull
    public static String globToRegex(@NotNull String glob) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        sb.append(".*");
                        i++;
                    } else {
                        sb.append("[^/]*");
                    }
                    break;
                case '?':
                    sb.append('.');
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                    sb.append('\\').append(c);
                    break;
                case '{':
                    sb.append('(');
                    break;
                case '}':
                    sb.append(')');
                    break;
                case ',':
                    sb.append('|');
                    break;
                case '[':
                    sb.append('[');
                    break;
                case ']':
                    sb.append(']');
                    break;
                default:
                    sb.append(c);
                    break;
            }
            i++;
        }
        sb.append('$');
        return sb.toString();
    }

    @NotNull
    public static Pattern globToRegexCached(@NotNull String glob) {
        return GLOB_CACHE.computeIfAbsent(glob, key -> Pattern.compile(globToRegex(key)));
    }

    public static boolean globMatchesPath(@NotNull String relPathUnix, @NotNull String glob) {
        String separator = FileSystems.getDefault().getSeparator();
        String relPathPlatform = separator.equals("/") ? relPathUnix : relPathUnix.replace('/', separator.charAt(0));
        PathMatcher matcher = PATH_MATCHER_CACHE.computeIfAbsent(glob, key -> {
            String pattern = separator.equals("/") ? key : key.replace('/', separator.charAt(0));
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        });
        Path path = Paths.get(relPathPlatform);
        return matcher.matches(path);
    }
}
