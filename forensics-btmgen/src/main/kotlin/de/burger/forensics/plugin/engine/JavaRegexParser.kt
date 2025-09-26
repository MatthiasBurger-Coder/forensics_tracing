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
        val sanitized = prefilterJava(text)
        val lineIndex = LineIndex(text)
        val pkgMatcher = PACKAGE_PATTERN.matcher(sanitized)
        val pkg = if (pkgMatcher.find()) pkgMatcher.group(1) else ""
        if (!packagePrefix.isNullOrBlank() && pkg.isNotBlank() && !pkg.startsWith(packagePrefix)) {
            return emptyList()
        }
        val classMatcher = CLASS_PATTERN.matcher(sanitized)
        while (classMatcher.find()) {
            val className = classMatcher.group(1)
            val fqcn = if (pkg.isBlank()) className else "$pkg.$className"
            val openIndex = sanitized.indexOf('{', classMatcher.end())
            if (openIndex < 0) continue
            val closeIndex = findMatchingBrace(sanitized, openIndex)
            val bodySanitized = sanitized.substring(openIndex + 1, closeIndex)
            val methodMatcher = METHOD_PATTERN.matcher(bodySanitized)
            while (methodMatcher.find()) {
                val methodName = methodMatcher.group(1)
                val methodStartInClass = openIndex + 1 + methodMatcher.start()
                val methodOpen = sanitized.indexOf('{', methodStartInClass)
                if (methodOpen < 0) continue
                val methodClose = findMatchingBrace(sanitized, methodOpen)
                val methodBodySanitized = sanitized.substring(methodOpen + 1, methodClose)
                val methodBodyOriginal = text.substring(methodOpen + 1, methodClose)
                if (includeEntryExit) {
                    rules += buildEntryRule(helperFqn, fqcn, methodName)
                    rules += buildExitRule(helperFqn, fqcn, methodName)
                }
                IF_REGEX.findAll(methodBodySanitized).forEach { match ->
                    val offset = methodOpen + 1 + match.range.first
                    val line = lineIndex.lineAt(offset)
                    val condRange = match.groups[1]?.range ?: return@forEach
                    val condRaw = methodBodyOriginal.substring(condRange)
                    val cond = escape(condRaw, maxStringLength)
                    rules += """
                        RULE ${fqcn}.${methodName}:${line}:if-true
                        CLASS $fqcn
                        METHOD ${methodName}(..)
                        HELPER $helperFqn
                        AT LINE $line
                        IF (${condRaw})
                        DO iff("$fqcn","$methodName",${line},"$cond", true)
                        ENDRULE
                    """.trimIndent()
                    rules += """
                        RULE ${fqcn}.${methodName}:${line}:if-false
                        CLASS $fqcn
                        METHOD ${methodName}(..)
                        HELPER $helperFqn
                        AT LINE $line
                        IF (!(${condRaw}))
                        DO iff("$fqcn","$methodName",${line},"$cond", false)
                        ENDRULE
                    """.trimIndent()
                }
                SWITCH_REGEX.findAll(methodBodySanitized).forEach { match ->
                    val offset = methodOpen + 1 + match.range.first
                    val line = lineIndex.lineAt(offset)
                    val selectRange = match.groups[1]?.range ?: return@forEach
                    val selectorRaw = methodBodyOriginal.substring(selectRange)
                    val sel = escape(selectorRaw, maxStringLength)
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
                CASE_REGEX.findAll(methodBodySanitized).forEach { match ->
                    val labelRange = match.groups[1]?.range ?: return@forEach
                    val labelOriginal = methodBodyOriginal.substring(labelRange)
                    val label = labelOriginal.replace(WHITESPACE_REGEX, " ").trim()
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

    private companion object {
        private val PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;")
        private val CLASS_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:@[\\w$.]+(?:\\([^)]*\\))?\\s*)*(?:(?:\\b(?:public|protected|private|abstract|final|static|strictfp|sealed)\\b|non-sealed)\\s+)*class\\s+([A-Za-z0-9_]+)"
        )
        private val METHOD_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:@[\\w$.]+(?:\\([^)]*\\))?\\s*)*(?:(?:\\b(?:public|protected|private|abstract|final|static|strictfp|synchronized|native|default)\\b)\\s+)*(?:<[^>]+>\\s*)?[\\w$<>\\[\\],.?\\s]+\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*\\{"
        )
        private val IF_REGEX = Regex("\\bif\\s*\\((.*?)\\)")
        private val SWITCH_REGEX = Regex("\\bswitch\\s*\\((.*?)\\)")
        private val CASE_REGEX = Regex("(?m)^[\\t ]*(case\\s+[^:]+|default)\\s*:")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
