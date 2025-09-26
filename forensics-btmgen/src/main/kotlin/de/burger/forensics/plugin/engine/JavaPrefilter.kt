package de.burger.forensics.plugin.engine

import kotlin.text.RegexOption

private val blockCommentRegex = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
private val lineCommentRegex = Regex("//.*?$", setOf(RegexOption.MULTILINE))
private val stringLiteralRegex = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
private val charLiteralRegex = Regex("'(?:[^'\\\\]|\\\\.)*'")

fun prefilterJava(source: String): String {
    val withoutBlock = blockCommentRegex.replace(source) { match ->
        blankWithNewlines(match.value)
    }
    val withoutLine = lineCommentRegex.replace(withoutBlock) { match ->
        blankWithNewlines(match.value)
    }
    val withoutStrings = stringLiteralRegex.replace(withoutLine) { match ->
        replaceLiteralPreservingLength(match.value, '"')
    }
    return charLiteralRegex.replace(withoutStrings) { match ->
        replaceLiteralPreservingLength(match.value, '\'')
    }
}

private fun blankWithNewlines(segment: String): String = buildString(segment.length) {
    segment.forEach { ch ->
        append(if (ch == '\n') '\n' else ' ')
    }
}

private fun replaceLiteralPreservingLength(literal: String, delimiter: Char): String {
    if (literal.length <= 2) {
        return delimiter.toString() + delimiter
    }
    return buildString(literal.length) {
        append(delimiter)
        repeat(literal.length - 2) { append(' ') }
        append(delimiter)
    }
}
