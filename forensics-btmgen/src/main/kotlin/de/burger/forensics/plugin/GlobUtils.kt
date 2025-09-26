package de.burger.forensics.plugin

import java.util.concurrent.ConcurrentHashMap

private val globCache = ConcurrentHashMap<String, Regex>()

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
