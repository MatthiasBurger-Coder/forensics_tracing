// DEST: src/test/java/de/burger/forensics/plugin/engine/JavaRegexParserTest.java
package de.burger.forensics.plugin.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JavaRegexParserTest {

    private final JavaRegexParser parser = new JavaRegexParser();

    @Test
    void scanHandlesAnnotatedClassesAndMethods() {
        String javaSource = String.join("\n",
                "package com.example;",
                "",
                "@Deprecated",
                "public final class SampleService {",
                "    @Override",
                "    public synchronized String execute(String input) {",
                "        if (input == null) {",
                "            return \"fallback\";",
                "        }",
                "        return input;",
                "    }",
                "}");

        List<String> rules = parser.scan(
                javaSource,
                "helper.Fqn",
                "com.example",
                true,
                200
        );

        assertTrue(rules.stream()
                        .anyMatch(rule -> rule.contains("RULE enter@com.example.SampleService.execute")),
                "Expected enter rule");
        assertTrue(rules.stream().anyMatch(rule ->
                        rule.contains("RULE com.example.SampleService.execute:") && rule.contains(":if-true")),
                "Expected if-true rule");
        assertTrue(rules.stream().anyMatch(rule ->
                        rule.contains("RULE com.example.SampleService.execute:") && rule.contains(":if-false")),
                "Expected if-false rule");
        assertTrue(rules.stream()
                        .anyMatch(rule -> rule.contains("RULE exit@com.example.SampleService.execute")),
                "Expected exit rule");
    }

    @Test
    void scanHandlesRepeatedModifiersOnNestedClasses() {
        String javaSource = String.join("\n",
                "package com.example;",
                "",
                "public class Outer {",
                "    public static final class InnerHelper {",
                "        @SafeVarargs",
                "        public static final <T> void process(T... items) {",
                "            switch (items.length) {",
                "                case 0:",
                "                    break;",
                "                default:",
                "                    break;",
                "            }",
                "        }",
                "    }",
                "}");

        List<String> rules = parser.scan(
                javaSource,
                "helper.Fqn",
                "com.example",
                true,
                200
        );

        assertTrue(rules.stream()
                        .anyMatch(rule -> rule.contains("RULE enter@com.example.InnerHelper.process")),
                "Expected enter rule for nested class");
        assertTrue(rules.stream().anyMatch(rule ->
                        rule.contains("RULE com.example.InnerHelper.process:") && rule.contains(":when")),
                "Expected switch rule");
        assertTrue(rules.stream().anyMatch(rule ->
                        rule.contains("RULE com.example.InnerHelper.process:") && rule.contains(":case")),
                "Expected case rule");
    }
}
