package de.burger.forensics.plugin.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaPrefilterTest {
    @Test
    fun `removes noise but preserves structure`() {
        val src = """
            package demo;
            // line comment with if (false) { fail(); }
            class A {
                String s = "keep \"quotes\" // not a comment";
                /* block
                   comment */
                if (x == "OK") { /*inline*/ System.out.println(x); } // tail comment
                char c = 'x';
            }
        """.trimIndent()

        val prefiltered = JavaPrefilter.prefilterJava(src)

        assertThat(prefiltered).doesNotContain("line comment")
        assertThat(prefiltered).doesNotContain("block\n                   comment")
        assertThat(prefiltered).doesNotContain("keep \"quotes\" // not a comment")
        assertThat(prefiltered).contains("class A {")
        assertThat(prefiltered).contains("if (x == \"")
        assertThat(prefiltered).doesNotContain("'x'")
        assertThat(prefiltered.count { it == '\n' }).isEqualTo(src.count { it == '\n' })
    }
}
