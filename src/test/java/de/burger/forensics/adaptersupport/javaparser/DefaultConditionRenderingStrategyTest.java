package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void rendersParameterPlaceholders() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                boolean check(int value) {
                    if (value > 0) {
                        return true;
                    }
                    return false;
                }
            }
            """);
        MethodScanContext context = new MethodScanContext(
            declaration,
            helperExtractor.parameterIndexes(declaration),
            helperExtractor.localVariableNames(declaration));
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();

        String rendered = strategy.renderCondition(condition, context);

        assertThat(rendered).isEqualTo("$1 > 0");
    }

    @Test
    void rendersLocalVariablesWithDollarPrefix() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                boolean check(int value) {
                    int limit = value + 1;
                    if (limit > value) {
                        return true;
                    }
                    return false;
                }
            }
            """);
        MethodScanContext context = new MethodScanContext(
            declaration,
            helperExtractor.parameterIndexes(declaration),
            helperExtractor.localVariableNames(declaration));
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();

        String rendered = strategy.renderCondition(condition, context);

        assertThat(rendered).isEqualTo("$limit > $1");
    }

    @Test
    void promotesInstanceFieldAccess() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                private int threshold = 10;
                boolean check(int value) {
                    if (value > threshold) {
                        return true;
                    }
                    return false;
                }
            }
            """);
        MethodScanContext context = new MethodScanContext(
            declaration,
            helperExtractor.parameterIndexes(declaration),
            helperExtractor.localVariableNames(declaration));
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();

        String rendered = strategy.renderCondition(condition, context);

        assertThat(rendered).isEqualTo("$1 > $this.threshold");
    }

    private MethodDeclaration parseMethod(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
    }
}
