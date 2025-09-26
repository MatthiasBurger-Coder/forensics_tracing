package de.burger.forensics.plugin.engine

import java.io.File

fun shouldSkipLargeFile(file: File, maxBytes: Long, debug: (String) -> Unit): Boolean {
    if (maxBytes <= 0) return false
    val length = file.length()
    if (length > maxBytes) {
        debug("Skipping large file (${length} bytes > limit $maxBytes): ${file.absolutePath}")
        return true
    }
    return false
}
