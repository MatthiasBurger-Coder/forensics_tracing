package de.burger.forensics.plugin

import org.jetbrains.kotlin.psi.KtElement

// Utility extension for retrieving 1-based line numbers for Kotlin PSI elements.
// Returns -1 if the document is not available.
@Suppress("unused")
private fun KtElement.getLineNumber(): Int =
    this.containingFile.viewProvider.document?.getLineNumber(this.textOffset)?.plus(1) ?: -1
