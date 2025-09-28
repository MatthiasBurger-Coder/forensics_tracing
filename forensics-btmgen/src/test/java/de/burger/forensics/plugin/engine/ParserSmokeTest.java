// DEST: src/test/java/de/burger/forensics/plugin/engine/ParserSmokeTest.java
package de.burger.forensics.plugin.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ParserSmokeTest {

    private final JavaRegexParser parser = new JavaRegexParser();

    @Test
    void detectsBranchesDespiteCommentsAndStrings() {
        String source = String.join("\n",
                "package smoke;",
                "",
                "class Sample {",
                "    void test(String value) {",
                "        // if (value.equals(\"no\")) { unreachable(); }",
                "        String marker = \"switch(value) { case \\\"x\\\": }\";",
                "        if (value.equals(\"ok\")) {",
                "            System.out.println(value);",
                "        }",
                "        switch (value) {",
                "            case \"x\":",
                "                break;",
                "            default:",
                "                break;",
                "        }",
                "    }",
                "}");

        List<String> rules = parser.scan(
                source,
                "helper.Fqn",
                "smoke",
                false,
                200
        );

        assertTrue(rules.stream()
                        .anyMatch(rule -> rule.contains(":if-true") && rule.contains("value.equals(\"ok\")")),
                "Should capture if-true branch despite comments");
        assertTrue(rules.stream().anyMatch(rule -> rule.contains(":when")),
                "Should capture switch statement");
        assertTrue(rules.stream().anyMatch(rule -> rule.contains(":case")),
                "Should capture switch case");
    }
}
