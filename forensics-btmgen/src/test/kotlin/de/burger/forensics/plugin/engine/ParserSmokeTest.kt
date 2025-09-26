package de.burger.forensics.plugin.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParserSmokeTest {
    private val parser = JavaRegexParser()

    @Test
    fun `detects branches despite comments and strings`() {
        val source = """
            package smoke;

            class Sample {
                void test(String value) {
                    // if (value.equals("no")) { unreachable(); }
                    String marker = "switch(value) { case \"x\": }";
                    if (value.equals("ok")) {
                        System.out.println(value);
                    }
                    switch (value) {
                        case "x":
                            break;
                        default:
                            break;
                    }
                }
            }
        """.trimIndent()

        val rules = parser.scan(
            text = source,
            helperFqn = "helper.Fqn",
            packagePrefix = "smoke",
            includeEntryExit = false,
            maxStringLength = 200
        )

        assertThat(rules).anySatisfy { assertThat(it).contains(":if-true").contains("value.equals(\"ok\")") }
        assertThat(rules).anySatisfy { assertThat(it).contains(":when") }
        assertThat(rules).anySatisfy { assertThat(it).contains(":case") }
    }
}
