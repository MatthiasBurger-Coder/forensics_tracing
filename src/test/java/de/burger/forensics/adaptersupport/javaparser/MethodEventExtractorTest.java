package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import de.burger.forensics.domain.model.RuleTemplate;
import de.burger.forensics.domain.model.ScanEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MethodEventExtractorTest {

    private MethodEventExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MethodEventExtractor(new DefaultConditionRenderingStrategy(new InstanceFieldNormalizer()));
    }

    @Test
    void collectsEventsFromTypicalMethod() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                int compute(int value) {
                    if (value > 10) {
                        throw new IllegalArgumentException();
                    }
                    switch (value) {
                        case 1 -> value = 2;
                        default -> value = 3;
                    }
                    return value;
                }
            }
            """);

        List<ScanEvent> events = extractor.collectMethodEvents(declaration, "example");

        assertThat(events).extracting(ScanEvent::kind)
            .contains(RuleTemplate.IF_TRUE, RuleTemplate.IF_FALSE, RuleTemplate.SWITCH,
                RuleTemplate.SWITCH_CASE, RuleTemplate.RETURN, RuleTemplate.THROW);
    }

    @Test
    void resolvesEnclosingTypeIncludingNesting() {
        CompilationUnit unit = StaticJavaParser.parse("""
            class Outer {
                class Inner {
                    void run() {}
                }
            }
            """);
        MethodDeclaration method = unit.findFirst(MethodDeclaration.class).orElseThrow();

        assertThat(extractor.resolveEnclosingType(method)).isEqualTo("Outer$Inner");
    }

    @Test
    void exposesParameterIndexesAndLocalVariables() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                void run(int value, String name) {
                    String suffix = name + value;
                }
            }
            """);

        Map<String, Integer> indexes = extractor.parameterIndexes(declaration);
        Set<String> locals = extractor.localVariableNames(declaration);

        assertThat(indexes).containsEntry("value", 1).containsEntry("name", 2);
        assertThat(locals).contains("suffix");
    }

    @Test
    void includesCatchParametersAsLocalVariables() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                void run() {
                    try {
                        risky();
                    } catch (RuntimeException ex) {
                        throw ex;
                    }
                }
            }
            """);

        List<ScanEvent> events = extractor.collectMethodEvents(declaration, "example");

        String throwCondition = events.stream()
                .filter(event -> event.kind() == RuleTemplate.THROW)
                .map(ScanEvent::conditionText)
                .findFirst()
                .orElseThrow();

        assertThat(throwCondition).isEqualTo("$ex");
    }

    private MethodDeclaration parseMethod(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
    }
}
