package de.burger.forensics.application.service;

import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;
import de.burger.forensics.domain.model.semantic.SemanticNode;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Matches scanner events to semantic graph nodes using stable source identity fields.
 */
public final class SemanticAnchorMatcher {

    private static final double FQCN_METHOD_LINE_CODE = 0.95d;
    private static final double FILE_METHOD_LINE = 0.80d;
    private static final double METHOD_LINE = 0.65d;
    private static final double LINE_ONLY = 0.40d;

    public List<SemanticAnchor> match(List<ScanEvent> events, List<SemanticNode> nodes) {
        Objects.requireNonNull(events, "Scan events must not be null.");
        Objects.requireNonNull(nodes, "Semantic nodes must not be null.");
        return events.stream()
                .map(event -> bestMatch(event, nodes))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<SemanticAnchor> bestMatch(ScanEvent event, List<SemanticNode> nodes) {
        SourceLocation location = event.location();
        if (location == null) {
            return Optional.empty();
        }
        return nodes.stream()
                .map(node -> anchor(event, node, score(location, event, node)))
                .filter(candidate -> candidate.confidence() > 0.0d)
                .max(Comparator.comparingDouble(SemanticAnchor::confidence));
    }

    private SemanticAnchor anchor(ScanEvent event, SemanticNode node, MatchScore score) {
        SourceLocation location = event.location();
        return new SemanticAnchor(
                scanEventKey(event),
                node.nodeId(),
                node.relativePath(),
                node.fqcn(),
                node.methodName(),
                node.signature(),
                location.line(),
                node.normalizedCode(),
                score.confidence(),
                score.strategy());
    }

    private MatchScore score(SourceLocation location, ScanEvent event, SemanticNode node) {
        boolean sameMethod = equalsText(location.method(), node.methodName());
        boolean sameLine = location.line() == node.lineNumber();
        boolean sameFqcn = equalsText(location.fqcn(), node.fqcn());
        boolean sameCode = event.conditionText() == null
                || event.conditionText().isBlank()
                || equalsText(event.conditionText(), node.normalizedCode());
        if (sameFqcn && sameMethod && sameLine && sameCode) {
            return new MatchScore(FQCN_METHOD_LINE_CODE, "FQCN_METHOD_LINE_CODE");
        }
        if (sameMethod && sameLine && !node.relativePath().isBlank()) {
            return new MatchScore(FILE_METHOD_LINE, "FILE_METHOD_LINE");
        }
        if (sameMethod && sameLine) {
            return new MatchScore(METHOD_LINE, "METHOD_LINE");
        }
        if (sameLine) {
            return new MatchScore(LINE_ONLY, "LINE_ONLY");
        }
        return new MatchScore(0.0d, "NO_MATCH");
    }

    private static boolean equalsText(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private static String scanEventKey(ScanEvent event) {
        SourceLocation location = event.location();
        String template = event.kind() == null ? "UNKNOWN" : event.kind().name();
        return location.fqcn() + "#" + location.method() + ":" + location.line() + ":" + template;
    }

    private record MatchScore(double confidence, String strategy) {
    }
}
