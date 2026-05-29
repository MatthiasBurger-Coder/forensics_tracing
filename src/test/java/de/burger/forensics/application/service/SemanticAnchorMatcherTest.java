package de.burger.forensics.application.service;

import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;
import de.burger.forensics.domain.model.semantic.SemanticNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticAnchorMatcherTest {

    @Test
    void matchesExactSourceIdentityWithHighestConfidence() {
        ScanEvent event = event("com.example.Foo", "run", 42, RuleTemplate.IF_TRUE, "value > 0");
        SemanticNode exact = node("exact", "com/example/Foo.java", "com.example.Foo", "run", 42, "value > 0");
        SemanticNode lineOnly = node("line", "com/example/Other.java", "com.example.Other", "other", 42, "other");

        List<SemanticAnchor> anchors = new SemanticAnchorMatcher().match(List.of(event), List.of(lineOnly, exact));

        assertThat(anchors).hasSize(1);
        assertThat(anchors.get(0).semanticNodeId()).isEqualTo("exact");
        assertThat(anchors.get(0).matchStrategy()).isEqualTo("FQCN_METHOD_LINE_CODE");
    }

    @Test
    void matchesLineOnlyWhenMethodIdentityDiffers() {
        ScanEvent event = event("com.example.Foo", "run", 42, RuleTemplate.IF_FALSE, "value < 0");
        SemanticNode node = node("line", "com/example/Foo.java", "com.example.Bar", "other", 42, "other");

        List<SemanticAnchor> anchors = new SemanticAnchorMatcher().match(List.of(event), List.of(node));

        assertThat(anchors).singleElement()
                .satisfies(anchor -> assertThat(anchor.matchStrategy()).isEqualTo("LINE_ONLY"));
    }

    @Test
    void ignoresEventsWithoutUsableLocationOrMatchingLine() {
        ScanEvent withoutLocation = new ScanEvent(null, null, null, null, "java", "void");
        ScanEvent withoutMatchingLine = event("com.example.Foo", "run", 99, null, null);
        SemanticNode node = node("line", "com/example/Foo.java", "com.example.Bar", "other", 42, "other");

        List<SemanticAnchor> anchors = new SemanticAnchorMatcher().match(
                List.of(withoutLocation, withoutMatchingLine),
                List.of(node));

        assertThat(anchors).isEmpty();
    }

    private static ScanEvent event(String fqcn, String method, int line, RuleTemplate template, String condition) {
        return new ScanEvent(new SourceLocation(fqcn, method, line), "()V", template, condition, "java", "void");
    }

    private static SemanticNode node(String id,
                                     String relativePath,
                                     String fqcn,
                                     String method,
                                     int line,
                                     String normalizedCode) {
        return new SemanticNode(id, "CALL", relativePath, fqcn, method, "()V", line, normalizedCode);
    }
}
