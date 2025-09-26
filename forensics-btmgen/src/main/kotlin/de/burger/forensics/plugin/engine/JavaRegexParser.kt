package de.burger.forensics.plugin.engine

import java.util.regex.Pattern

class JavaRegexParser : JavaScanner {
    override fun scan(
        text: String,
        helperFqn: String,
        packagePrefix: String?,
        includeEntryExit: Boolean,
        maxStringLength: Int
    ): List<String> {
        val rules = mutableListOf<String>()
        val pkgRegex = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;")
        val pkgMatcher = pkgRegex.matcher(text)
        val pkg = if (pkgMatcher.find()) pkgMatcher.group(1) else ""
        if (!packagePrefix.isNullOrBlank() && pkg.isNotBlank() && !pkg.startsWith(packagePrefix)) {
            return emptyList()
        }
        val classRegex = Pattern.compile(
            "(?m)^\\s*(?:@[\\w$.]+(?:\\([^)]*\\))?\\s*)*(?:(?:\\b(?:public|protected|private|abstract|final|static|strictfp|sealed)\\b|non-sealed)\\s+)*class\\s+([A-Za-z0-9_]+)"
        )
        val classMatcher = classRegex.matcher(text)
        while (classMatcher.find()) {
            val className = classMatcher.group(1)
            val fqcn = if (pkg.isBlank()) className else "$pkg.$className"
            val openIndex = text.indexOf('{', classMatcher.end())
            if (openIndex < 0) continue
            val closeIndex = findMatchingBrace(text, openIndex)
            val bodyText = text.substring(openIndex + 1, closeIndex)
            val methodRegex = Pattern.compile(
                "(?m)^\\s*(?:@[\\w$.]+(?:\\([^)]*\\))?\\s*)*(?:(?:\\b(?:public|protected|private|abstract|final|static|strictfp|synchronized|native|default)\\b)\\s+)*(?:<[^>]+>\\s*)?[\\w$<>\\[\\],.?\\s]+\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*\\{"
            )
            val methodMatcher = methodRegex.matcher(bodyText)
            while (methodMatcher.find()) {
                val methodName = methodMatcher.group(1)
                val methodStartInClass = openIndex + 1 + methodMatcher.start()
                val methodOpen = text.indexOf('{', methodStartInClass)
                if (methodOpen < 0) continue
                val methodClose = findMatchingBrace(text, methodOpen)
                val methodBody = text.substring(methodOpen + 1, methodClose)
                val lineIndex = LineIndex(text)
                if (includeEntryExit) {
                    rules += buildEntryRule(helperFqn, fqcn, methodName)
                    rules += buildExitRule(helperFqn, fqcn, methodName)
                }
                // if/else
                val ifRegex = Regex("\\bif\\s*\\((.*?)\\)")
                ifRegex.findAll(methodBody).forEach { match ->
                    val offset = methodOpen + 1 + match.range.first
                    val line = lineIndex.lineAt(offset)
                    val cond = escape(match.groupValues[1], maxStringLength)
                    rules += """
                        RULE ${fqcn}.${methodName}:${line}:if-true
                        CLASS $fqcn
                        METHOD ${methodName}(..)
                        HELPER $helperFqn
                        AT LINE $line
                        IF (${match.groupValues[1]})
                        DO iff("$fqcn","$methodName",${line},"$cond", true)
                        ENDRULE
                    """.trimIndent()
                    rules += """
                        RULE ${fqcn}.${methodName}:${line}:if-false
                        CLASS $fqcn
                        METHOD ${methodName}(..)
                        HELPER $helperFqn
                        AT LINE $line
                        IF (!(${match.groupValues[1]}))
                        DO iff("$fqcn","$methodName",${line},"$cond", false)
                        ENDRULE
                    """.trimIndent()
                }
                // switch
                val switchRegex = Regex("\\bswitch\\s*\\((.*?)\\)")
                switchRegex.findAll(methodBody).forEach { match ->
                    val offset = methodOpen + 1 + match.range.first
                    val line = lineIndex.lineAt(offset)
                    val sel = escape(match.groupValues[1], maxStringLength)
                    rules += """
                        RULE ${fqcn}.${methodName}:${line}:when
                        CLASS $fqcn
                        METHOD ${methodName}(..)
                        HELPER $helperFqn
                        AT LINE $line
                        DO sw("$fqcn","$methodName",${line},"${sel}")
                        ENDRULE
                    """.trimIndent()
                }
                val caseRegex = Regex("(?m)^[\\t ]*(case\\s+[^:]+|default)\\s*:")
                caseRegex.findAll(methodBody).forEach { match ->
                    val label = match.groupValues[1].replace(Regex("\\s+"), " ").trim()
                    val offset = methodOpen + 1 + match.range.first
                    val line = lineIndex.lineAt(offset)
                    val esc = escape(label, maxStringLength)
                    rules += """
                        RULE ${fqcn}.${methodName}:${line}:case
                        CLASS $fqcn
                        METHOD ${methodName}(..)
                        HELPER $helperFqn
                        AT LINE $line
                        DO kase("$fqcn","$methodName",${line},"$esc")
                        ENDRULE
                    """.trimIndent()
                }
            }
        }
        return rules
    }

    private fun escape(value: String, limit: Int): String {
        val truncated = if (limit > 0 && value.length > limit) value.take(limit) + "…" else value
        return truncated
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun buildEntryRule(helper: String, className: String, methodName: String): String =
        """
        RULE enter@${className}.${methodName}
        CLASS ${className}
        METHOD ${methodName}(..)
        HELPER ${helper}
        AT ENTRY
        DO enter("${className}","${methodName}", ${'$'}LINE)
        ENDRULE
        """.trimIndent()

    private fun buildExitRule(helper: String, className: String, methodName: String): String =
        """
        RULE exit@${className}.${methodName}
        CLASS $className
        METHOD ${methodName}(..)
        HELPER $helper
        AT EXIT
        DO exit("$className","$methodName", ${'$'}LINE)
        ENDRULE
        """.trimIndent()

    private fun findMatchingBrace(text: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return text.length - 1
    }

    private class LineIndex(text: String) {
        private val starts: IntArray
        init {
            val tmp = mutableListOf(0)
            text.forEachIndexed { idx, c -> if (c == '\n') tmp += idx + 1 }
            starts = tmp.toIntArray()
        }
        fun lineAt(offset: Int): Int {
            if (starts.isEmpty()) return 1
            var low = 0
            var high = starts.size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val s = starts[mid]
                val next = if (mid + 1 < starts.size) starts[mid + 1] else Int.MAX_VALUE
                when {
                    offset < s -> high = mid - 1
                    offset >= next -> low = mid + 1
                    else -> return mid + 1
                }
            }
            return starts.size
        }
    }
}