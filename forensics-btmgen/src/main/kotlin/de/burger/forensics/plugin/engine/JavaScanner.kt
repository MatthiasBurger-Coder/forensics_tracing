package de.burger.forensics.plugin.engine

interface JavaScanner {
    /**
     * Scans a Java source text and returns Byteman rules for methods/branches found.
     * The emitter uses METHOD(name(..)) and escapes/truncates strings as needed.
     */
    fun scan(text: String, helperFqn: String, packagePrefix: String?, includeEntryExit: Boolean, maxStringLength: Int = 0): List<String>
}