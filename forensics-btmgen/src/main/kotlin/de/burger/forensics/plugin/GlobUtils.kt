package de.burger.forensics.plugin

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.PathMatcher
import java.util.concurrent.ConcurrentHashMap

private val globCache = ConcurrentHashMap<String, Regex>()
private val pathMatcherCache = ConcurrentHashMap<String, PathMatcher>()

// This must be pure; never call globToRegexCached() from here. No caching, no indirect recursion.
fun globToRegex(glob: String): String {
    val sb = StringBuilder("^")
    var i = 0
    while (i < glob.length) {
        when (val c = glob[i]) {
            '*' -> {
                if (i + 1 < glob.length && glob[i + 1] == '*') {
                    sb.append(".*")
                    i++
                } else {
                    sb.append("[^/]*")
                }
            }
            '?' -> sb.append('.')
            '.', '(', ')', '+', '|', '^', '$', '@', '%' -> sb.append('\\').append(c)
            '{' -> sb.append('(')
            '}' -> sb.append(')')
            ',' -> sb.append('|')
            '[' -> sb.append('[')
            ']' -> sb.append(']')
            else -> sb.append(c)
        }
        i++
    }
    sb.append('$')
    return sb.toString()
}

fun globToRegexCached(glob: String): Regex = globCache.computeIfAbsent(glob) { Regex(globToRegex(it)) }

fun globMatchesPath(relPathUnix: String, glob: String): Boolean {
    // Normalize '/' used in our code to the platform separator for PathMatcher
    val sep = FileSystems.getDefault().separator
    val relPathPlatform = if (sep == "/") relPathUnix else relPathUnix.replace('/', sep[0])
    val matcher = pathMatcherCache.computeIfAbsent(glob) {
        val pattern = if (sep == "/") glob else glob.replace('/', sep[0])
        FileSystems.getDefault().getPathMatcher("glob:" + pattern)
    }
    val p: Path = Paths.get(relPathPlatform)
    return matcher.matches(p)
}
