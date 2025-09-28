// DEST: src/test/java/de/burger/forensics/plugin/engine/JavaPrefilterTest.java
package de.burger.forensics.plugin.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JavaPrefilterTest {

    @Test
    void removesNoiseButPreservesStructure() {
        String src = String.join("\n",
                "package demo;",
                "// line comment with if (false) { fail(); }",
                "class A {",
                "    String s = \"keep \\\"quotes\\\" // not a comment\";",
                "    /* block",
                "       comment */",
                "    if (x == \"OK\") { /*inline*/ System.out.println(x); } // tail comment",
                "    char c = 'x';",
                "}");

        String prefiltered = JavaPrefilter.prefilterJava(src);

        assertFalse(prefiltered.contains("line comment"));
        assertFalse(prefiltered.contains("block\n                   comment"));
        assertFalse(prefiltered.contains("keep \"quotes\" // not a comment"));
        assertTrue(prefiltered.contains("class A {"));
        assertTrue(prefiltered.contains("if (x == \""));
        assertFalse(prefiltered.contains("'x'"));
        long prefilteredNewlines = prefiltered.chars().filter(ch -> ch == '\n').count();
        long originalNewlines = src.chars().filter(ch -> ch == '\n').count();
        assertEquals(originalNewlines, prefilteredNewlines,
                "Prefiltering should preserve newline structure");
    }
}
