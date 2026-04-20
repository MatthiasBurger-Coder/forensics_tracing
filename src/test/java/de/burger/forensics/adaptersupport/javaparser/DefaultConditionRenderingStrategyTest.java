package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConditionRenderingStrategyTest {

    private InstanceFieldNormalizer normalizer;
    private DefaultConditionRenderingStrategy strategy;
    private MethodEventExtractor helperExtractor;

    @BeforeEach
    void setUp() {
        normalizer = new InstanceFieldNormalizer();
        strategy = new DefaultConditionRenderingStrategy(normalizer);
        helperExtractor = new MethodEventExtractor(strategy);
    }

    @ParameterizedTest
    @MethodSource("renderConditionCases")
    void rendersExpectedConditions(String source, String expected) {
        MethodDeclaration declaration = parseMethod(source);
        MethodScanContext context = new MethodScanContext(
            declaration,
            helperExtractor.parameterIndexes(declaration),
            helperExtractor.localVariableNames(declaration));
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();

        String rendered = strategy.renderCondition(condition, context);

        assertThat(rendered).isEqualTo(expected);
    }

    private static Stream<Arguments> renderConditionCases() {
        return Stream.of(
            Arguments.of("""
                class Sample {
                    boolean check(int value) {
                        if (value > 0) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "$1 > 0"),
            Arguments.of("""
                class Sample {
                    boolean check(int value) {
                        int limit = value + 1;
                        if (limit > value) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "$limit > $1"),
            Arguments.of("""
                class Sample {
                    private int threshold = 10;
                    boolean check(int value) {
                        if (value > threshold) {
                            return true;
                        }
                        return false;
                    }
                }
                """, "$1 > $this.threshold"),
            Arguments.of("""
                class Sample {
                    private static Sample INSTANCE;
                    Sample getInstance() {
                        if (INSTANCE == null) {
                            INSTANCE = new Sample();
                        }
                        return INSTANCE;
                    }
                }
                """, "$CLASS.INSTANCE == null")
        );
    }

    private MethodDeclaration parseMethod(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
    }
}
