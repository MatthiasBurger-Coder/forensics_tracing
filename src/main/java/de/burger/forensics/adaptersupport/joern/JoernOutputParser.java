package de.burger.forensics.adaptersupport.joern;

import de.burger.forensics.domain.model.analysis.ArtifactChecksum;
import de.burger.forensics.domain.model.semantic.CallRelation;
import de.burger.forensics.domain.model.semantic.ControlFlowRelation;
import de.burger.forensics.domain.model.semantic.DataFlowPath;
import de.burger.forensics.domain.model.semantic.DataFlowStep;
import de.burger.forensics.domain.model.semantic.SemanticAnchor;
import de.burger.forensics.domain.model.semantic.SemanticAnalysisResult;
import de.burger.forensics.domain.model.semantic.SemanticEdge;
import de.burger.forensics.domain.model.semantic.SemanticMethod;
import de.burger.forensics.domain.model.semantic.SemanticNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

/**
 * Parses deterministic JSON artifacts exported by the Joern CLI adapter.
 */
public final class JoernOutputParser {

    public SemanticAnalysisResult parse(
            JoernArtifactPaths paths,
            List<ArtifactChecksum> artifacts,
            String providerVersion,
            String semanticFingerprint
    ) {
        Objects.requireNonNull(paths, "Artifact paths must not be null.");
        String callgraph = readIfExists(paths.callgraph());
        String controlflow = readIfExists(paths.controlflow());
        String dataflow = readIfExists(paths.dataflow());
        String slices = readIfExists(paths.slices());
        return new SemanticAnalysisResult(
                providerVersion,
                semanticFingerprint,
                artifacts,
                JsonArray.objects(callgraph, "nodes").stream().map(this::node).toList(),
                JsonArray.objects(callgraph, "edges").stream().map(this::edge).toList(),
                JsonArray.objects(callgraph, "methods").stream().map(this::method).toList(),
                JsonArray.objects(callgraph, "calls").stream().map(this::call).toList(),
                JsonArray.objects(controlflow, "relations").stream().map(this::controlFlow).toList(),
                JsonArray.objects(dataflow, "paths").stream().map(this::dataFlowPath).toList(),
                JsonArray.objects(slices, "anchors").stream().map(this::anchor).toList());
    }

    private SemanticNode node(String object) {
        return new SemanticNode(
                JsonField.text(object, "id"),
                JsonField.text(object, "type"),
                JsonField.text(object, "file"),
                JsonField.optionalText(object, "fqcn"),
                JsonField.text(object, "method"),
                JsonField.optionalText(object, "signature"),
                JsonField.integer(object, "line"),
                JsonField.optionalText(object, "code"));
    }

    private SemanticEdge edge(String object) {
        return new SemanticEdge(
                JsonField.text(object, "id"),
                JsonField.text(object, "source"),
                JsonField.text(object, "target"),
                JsonField.text(object, "type"));
    }

    private SemanticMethod method(String object) {
        return new SemanticMethod(
                JsonField.text(object, "id"),
                JsonField.text(object, "file"),
                JsonField.text(object, "fqcn"),
                JsonField.text(object, "name"),
                JsonField.optionalText(object, "signature"),
                JsonField.integer(object, "line"));
    }

    private CallRelation call(String object) {
        return new CallRelation(
                JsonField.text(object, "caller"),
                JsonField.text(object, "callee"),
                JsonField.text(object, "node"));
    }

    private ControlFlowRelation controlFlow(String object) {
        return new ControlFlowRelation(
                JsonField.text(object, "source"),
                JsonField.text(object, "target"),
                JsonField.text(object, "type"));
    }

    private DataFlowPath dataFlowPath(String object) {
        return new DataFlowPath(
                JsonField.text(object, "id"),
                JsonField.text(object, "source"),
                JsonField.text(object, "target"),
                JsonArray.objects(object, "steps").stream()
                        .map(step -> new DataFlowStep(
                                JsonField.text(step, "node"),
                                JsonField.integer(step, "order"),
                                JsonField.text(step, "kind")))
                        .toList());
    }

    private SemanticAnchor anchor(String object) {
        return new SemanticAnchor(
                JsonField.text(object, "scanEventKey"),
                JsonField.text(object, "node"),
                JsonField.text(object, "file"),
                JsonField.optionalText(object, "fqcn"),
                JsonField.text(object, "method"),
                JsonField.optionalText(object, "signature"),
                JsonField.integer(object, "line"),
                JsonField.optionalText(object, "code"),
                JsonField.decimal(object, "confidence"),
                JsonField.text(object, "strategy"));
    }

    private static String readIfExists(java.nio.file.Path file) {
        if (!Files.exists(file)) {
            return "{}";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Joern artifact " + file + ".", e);
        }
    }

    private static final class JsonArray {

        private JsonArray() {
        }

        static List<String> objects(String json, String arrayName) {
            int arrayStart = json.indexOf("\"" + arrayName + "\"");
            if (arrayStart < 0) {
                return List.of();
            }
            int openBracket = json.indexOf('[', arrayStart);
            int closeBracket = matching(json, openBracket, '[', ']');
            if (openBracket < 0 || closeBracket < 0) {
                return List.of();
            }
            return splitObjects(json.substring(openBracket + 1, closeBracket));
        }

        private static List<String> splitObjects(String arrayBody) {
            java.util.ArrayList<String> objects = new java.util.ArrayList<>();
            int cursor = 0;
            while (cursor < arrayBody.length()) {
                int openBrace = arrayBody.indexOf('{', cursor);
                if (openBrace < 0) {
                    break;
                }
                int closeBrace = matching(arrayBody, openBrace, '{', '}');
                if (closeBrace < 0) {
                    break;
                }
                objects.add(arrayBody.substring(openBrace, closeBrace + 1));
                cursor = closeBrace + 1;
            }
            return List.copyOf(objects);
        }

        private static int matching(String text, int openIndex, char open, char close) {
            if (openIndex < 0) {
                return -1;
            }
            int depth = 0;
            boolean inString = false;
            for (int index = openIndex; index < text.length(); index++) {
                char current = text.charAt(index);
                boolean escaped = index > 0 && text.charAt(index - 1) == '\\';
                if (current == '"' && !escaped) {
                    inString = !inString;
                }
                if (inString) {
                    continue;
                }
                if (current == open) {
                    depth++;
                }
                if (current == close) {
                    depth--;
                    if (depth == 0) {
                        return index;
                    }
                }
            }
            return -1;
        }
    }

    private static final class JsonField {

        private JsonField() {
        }

        static String text(String object, String fieldName) {
            String value = optionalText(object, fieldName);
            if (value == null || value.isBlank()) {
                throw new JoernAnalysisException("Missing required Joern field: " + fieldName);
            }
            return value;
        }

        static String optionalText(String object, String fieldName) {
            String marker = "\"" + fieldName + "\"";
            int fieldStart = object.indexOf(marker);
            if (fieldStart < 0) {
                return null;
            }
            int colon = object.indexOf(':', fieldStart + marker.length());
            int firstQuote = object.indexOf('"', colon + 1);
            if (colon < 0 || firstQuote < 0) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            boolean escaped = false;
            for (int index = firstQuote + 1; index < object.length(); index++) {
                char current = object.charAt(index);
                if (escaped) {
                    builder.append(unescape(current));
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '"') {
                    return builder.toString();
                }
                builder.append(current);
            }
            return null;
        }

        static int integer(String object, String fieldName) {
            return Integer.parseInt(number(object, fieldName));
        }

        static double decimal(String object, String fieldName) {
            return Double.parseDouble(number(object, fieldName));
        }

        private static String number(String object, String fieldName) {
            String marker = "\"" + fieldName + "\"";
            int fieldStart = object.indexOf(marker);
            if (fieldStart < 0) {
                throw new JoernAnalysisException("Missing required Joern field: " + fieldName);
            }
            int colon = object.indexOf(':', fieldStart + marker.length());
            if (colon < 0) {
                throw new JoernAnalysisException("Invalid Joern field: " + fieldName);
            }
            int cursor = colon + 1;
            while (cursor < object.length() && Character.isWhitespace(object.charAt(cursor))) {
                cursor++;
            }
            int end = cursor;
            while (end < object.length() && "-0123456789.".indexOf(object.charAt(end)) >= 0) {
                end++;
            }
            if (end == cursor) {
                throw new JoernAnalysisException("Invalid numeric Joern field: " + fieldName);
            }
            return object.substring(cursor, end);
        }

        private static char unescape(char escaped) {
            return switch (escaped) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'b' -> '\b';
                case 'f' -> '\f';
                default -> escaped;
            };
        }
    }
}
