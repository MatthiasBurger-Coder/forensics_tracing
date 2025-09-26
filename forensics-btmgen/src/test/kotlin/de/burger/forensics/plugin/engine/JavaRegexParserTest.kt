package de.burger.forensics.plugin.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class JavaRegexParserTest {
    private val parser = JavaRegexParser()

    @Test
    fun `scan handles annotated classes and methods`() {
        val javaSource = """
            package com.example;

            @Deprecated
            public final class SampleService {
                @Override
                public synchronized String execute(String input) {
                    if (input == null) {
                        return \"fallback\";
                    }
                    return input;
                }
            }
        """.trimIndent()

        val rules = parser.scan(
            text = javaSource,
            helperFqn = "helper.Fqn",
            packagePrefix = "com.example",
            includeEntryExit = true,
            maxStringLength = 200
        )

        assertTrue(rules.any { it.contains("RULE enter@com.example.SampleService.execute") })
        assertTrue(rules.any { it.contains("RULE com.example.SampleService.execute:") && it.contains(":if-true") })
        assertTrue(rules.any { it.contains("RULE com.example.SampleService.execute:") && it.contains(":if-false") })
        assertTrue(rules.any { it.contains("RULE exit@com.example.SampleService.execute") })
    }

    @Test
    fun `scan handles repeated modifiers on nested classes`() {
        val javaSource = """
            package com.example;

            public class Outer {
                public static final class InnerHelper {
                    @SafeVarargs
                    public static final <T> void process(T... items) {
                        switch (items.length) {
                            case 0:
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        """.trimIndent()

        val rules = parser.scan(
            text = javaSource,
            helperFqn = "helper.Fqn",
            packagePrefix = "com.example",
            includeEntryExit = true,
            maxStringLength = 200
        )

        assertTrue(rules.any { it.contains("RULE enter@com.example.InnerHelper.process") })
        assertTrue(rules.any { it.contains("RULE com.example.InnerHelper.process:") && it.contains(":when") })
        assertTrue(rules.any { it.contains("RULE com.example.InnerHelper.process:") && it.contains(":case") })
    }
}
