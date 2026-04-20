package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StaticFieldQualifierTest {

    private final StaticFieldQualifier qualifier = new StaticFieldQualifier();

    @Test
    void identifiesAndQualifiesStaticFieldAccess() {
        MethodDeclaration declaration = parseMethod("""
            class Sample {
                private static int VALUE = 1;
                boolean check(int limit) {
                    if (VALUE > limit) {
                        return true;
                    }
                    return false;
                }
            }
            """);
        Expression condition = declaration.findFirst(IfStmt.class).orElseThrow().getCondition();
        NameExpr staticField = condition.findFirst(NameExpr.class, name -> name.getNameAsString().equals("VALUE")).orElseThrow();

        Set<Range> ranges = qualifier.identifyStaticFieldRanges(condition, Set.of("limit"));
        boolean replaced = qualifier.qualifyStaticFieldAccess(staticField, ranges);

        assertThat(replaced).isTrue();
        assertThat(condition).hasToString("$CLASS.VALUE > limit");
    }

    @Test
    void ignoresIdentifiersShadowedByParametersLocalsLambdaAndCatchClauses() {
        MethodDeclaration parameterMethod = parseMethod("""
            class Sample {
                private static int VALUE = 1;
                boolean check(int VALUE) {
                    if (VALUE > 0) {
                        return true;
                    }
                    return false;
                }
            }
            """);
        NameExpr parameter = parameterMethod.findFirst(NameExpr.class, name -> name.getNameAsString().equals("VALUE")).orElseThrow();

        MethodDeclaration lambdaMethod = parseMethod("""
            class Sample {
                private static int VALUE = 1;
                int check() {
                    java.util.function.IntUnaryOperator mapper = VALUE -> VALUE + 1;
                    return mapper.applyAsInt(1);
                }
            }
            """);
        NameExpr lambdaParameter = lambdaMethod.findAll(NameExpr.class).stream()
            .filter(name -> name.getNameAsString().equals("VALUE"))
            .findFirst()
            .orElseThrow();

        MethodDeclaration catchMethod = parseMethod("""
            class Sample {
                private static RuntimeException VALUE = new RuntimeException();
                void check() {
                    try {
                        throw VALUE;
                    } catch (RuntimeException VALUE) {
                        throw VALUE;
                    }
                }
            }
            """);
        NameExpr catchParameter = catchMethod.findAll(NameExpr.class).stream()
            .filter(name -> name.getNameAsString().equals("VALUE"))
            .reduce((first, second) -> second)
            .orElseThrow();

        assertThat(qualifier.isLikelyStaticField(parameter, "VALUE", Set.of())).isFalse();
        assertThat(qualifier.isLikelyStaticField(lambdaParameter, "VALUE", Set.of())).isFalse();
        assertThat(qualifier.isLikelyStaticField(catchParameter, "VALUE", Set.of())).isFalse();
        assertThat(qualifier.isLikelyStaticField(parameter, "VALUE", Set.of("VALUE"))).isFalse();
    }

    @Test
    void supportsEnumStaticFieldsAndRejectsEmptyIdentifiers() {
        MethodDeclaration declaration = parseMethod("""
            enum Sample {
                ITEM;
                static int VALUE = 1;
                boolean check() {
                    return VALUE > 0;
                }
            }
            """);
        NameExpr staticField = declaration.findFirst(NameExpr.class, name -> name.getNameAsString().equals("VALUE")).orElseThrow();

        assertThat(qualifier.declaresStaticField(staticField, "VALUE")).isTrue();
        assertThat(qualifier.declaresStaticField(staticField, "")).isFalse();
        NameExpr missing = StaticJavaParser.parseExpression("missing").asNameExpr();
        assertThat(qualifier.resolvesToStaticField(missing)).isFalse();
    }

    @Test
    void returnsFalseWhenNoStaticRangeWasRecorded() {
        NameExpr name = StaticJavaParser.parseExpression("VALUE").asNameExpr();

        assertThat(qualifier.qualifyStaticFieldAccess(name, Set.of())).isFalse();
    }

    private static MethodDeclaration parseMethod(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
    }
}
