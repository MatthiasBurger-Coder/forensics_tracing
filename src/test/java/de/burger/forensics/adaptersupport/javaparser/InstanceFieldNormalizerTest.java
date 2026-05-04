package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InstanceFieldNormalizerTest {

    private final InstanceFieldNormalizer normalizer = new InstanceFieldNormalizer();

    @Test
    void identifiesDeclaredInstanceFields() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                private int value;
                void check(int limit) {
                    if (value > limit) {}
                }
            }
            """);
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();

        Set<Range> ranges = normalizer.identifyInstanceFieldRanges(condition, Set.of("limit"));

        assertThat(ranges).hasSize(1);
    }

    @Test
    void promotesInstanceFieldAccessToThisPlaceholder() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                private int value;
                void check(int limit) {
                    if (value > limit) {}
                }
            }
            """);
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();
        Set<Range> ranges = normalizer.identifyInstanceFieldRanges(condition, Set.of("limit"));
        NameExpr nameExpr = condition.findFirst(NameExpr.class, name -> name.getNameAsString().equals("value")).orElseThrow();

        normalizer.promoteInstanceFieldAccess(nameExpr, ranges);

        assertThat(condition.toString()).contains("$this.value");
    }

    @Test
    void treatsParametersAndLocalsAsNonFields() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                void check(int value) {
                    int limit = value + 1;
                    if (limit > value) {}
                }
            }
            """);
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();
        NameExpr parameter = condition.findFirst(NameExpr.class, name -> name.getNameAsString().equals("value")).orElseThrow();
        NameExpr local = condition.findFirst(NameExpr.class, name -> name.getNameAsString().equals("limit")).orElseThrow();

        assertThat(normalizer.isLikelyInstanceField(parameter, "value", Set.of("limit"))).isFalse();
        assertThat(normalizer.isLikelyInstanceField(local, "limit", Set.of("limit"))).isFalse();
    }

    @Test
    void supportsEnumInstanceFieldsAndRejectsEnumStaticFields() {
        MethodDeclaration declaration = parseMethod("""
            enum Sample {
                ITEM;
                private int value;
                private static int STATIC_VALUE;
                boolean check() {
                    if (value > STATIC_VALUE) {}
                    return true;
                }
            }
            """);
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();
        NameExpr instanceField = condition.findFirst(NameExpr.class, name -> name.getNameAsString().equals("value")).orElseThrow();
        NameExpr staticField = condition.findFirst(NameExpr.class, name -> name.getNameAsString().equals("STATIC_VALUE")).orElseThrow();

        assertThat(normalizer.declaresInstanceField(instanceField, "value")).isTrue();
        assertThat(normalizer.declaresInstanceField(staticField, "STATIC_VALUE")).isFalse();
    }

    @Test
    void treatsStackOverflowDuringResolutionAsNonInstanceField() {
        NameExpr name = new StackOverflowNameExpr("value");

        assertThatCode(() -> assertThat(normalizer.resolvesToInstanceField(name)).isFalse())
            .doesNotThrowAnyException();
    }

    private MethodDeclaration parseMethod(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
    }

    private static final class StackOverflowNameExpr extends NameExpr {

        private StackOverflowNameExpr(String name) {
            super(name);
        }

        @Override
        public ResolvedValueDeclaration resolve() {
            throw new StackOverflowError("simulated JavaParser recursion");
        }
    }
}
