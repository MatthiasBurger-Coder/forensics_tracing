package de.burger.forensics.plugin.scan

import de.burger.forensics.plugin.scan.java.JavaAstScanner
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaAstScannerFiltersTest {

    @Test
    fun `includes all when include list is empty and excludes none when exclude list is empty`() {
        val root = Files.createTempDirectory("java-ast-inc-exc")
        val keep = root.resolve("Keep.java")
        val other = root.resolve("Other.java")
        Files.writeString(
            keep,
            """
            package com.test.keep;
            public class Keep { public void m(){ if (true) { } } }
            """.trimIndent()
        )
        Files.writeString(
            other,
            """
            package com.test.other;
            public class Other { public void m(){ if (true) { } } }
            """.trimIndent()
        )

        val events = JavaAstScanner().scan(root, emptyList(), emptyList())
        assertThat(events).isNotEmpty
        // With empty filters, we should see events from both packages
        assertThat(events.map { it.fqcn }.toSet())
            .contains("com.test.keep.Keep", "com.test.other.Other")
    }

    @Test
    fun `applies include and exclude packages with prefix semantics (min and max boundary cases)`() {
        val root = Files.createTempDirectory("java-ast-filtered")
        val incOk = root.resolve("A.java")
        val incExcluded = root.resolve("B.java")
        val notIncluded = root.resolve("C.java")
        Files.writeString(
            incOk,
            """
            package com.keep;
            public class A { public void a(){ if (true) {} } }
            """.trimIndent()
        )
        Files.writeString(
            incExcluded,
            """
            package com.keep.excluded.sub;
            public class B { public void b(){ if (true) {} } }
            """.trimIndent()
        )
        Files.writeString(
            notIncluded,
            """
            package com.drop;
            public class C { public void c(){ if (true) {} } }
            """.trimIndent()
        )

        val includePkgs = listOf("com.keep") // minimal prefix include
        val excludePkgs = listOf("com.keep.excluded") // maximal narrower exclusion

        val events = JavaAstScanner().scan(root, includePkgs, excludePkgs)
        val fqcnSet = events.map { it.fqcn }.toSet()

        // Only A should be present
        assertThat(fqcnSet).contains("com.keep.A")
        assertThat(fqcnSet).doesNotContain("com.keep.excluded.sub.B")
        assertThat(fqcnSet).doesNotContain("com.drop.C")
    }

    @Test
    fun `resolves nested type names and default switch case label`() {
        val root = Files.createTempDirectory("java-ast-nested")
        val file = root.resolve("Outer.java")
        Files.writeString(
            file,
            """
            package com.example;
            public class Outer {
                public class Inner {
                    public int m(int v) {
                        switch (v) { default: return 1; }
                    }
                }
            }
            """.trimIndent()
        )

        val events = JavaAstScanner().scan(root, emptyList(), emptyList())
        assertThat(events).isNotEmpty
        // Verify nested class FQCN resolution
        assertThat(events.map { it.fqcn }.toSet())
            .contains("com.example.Outer\$Inner")
        // Verify switch and default case labeling
        assertThat(events).anySatisfy { e ->
            if (e.kind == "switch") {
                assertThat(e.conditionText).isEqualTo("v")
            }
        }
        assertThat(events).anySatisfy { e ->
            if (e.kind == "switch-case") {
                assertThat(e.conditionText).isEqualTo("default")
            }
        }
    }
}
