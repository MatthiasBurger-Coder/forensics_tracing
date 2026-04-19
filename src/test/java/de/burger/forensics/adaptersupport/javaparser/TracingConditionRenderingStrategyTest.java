package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TracingConditionRenderingStrategyTest {

    @Test
    void delegatesAndRecordsRenderedFragments() {
        List<String> traces = new ArrayList<>();
        TracingConditionRenderingStrategy strategy = new TracingConditionRenderingStrategy(new StubStrategy(), traces);
        MethodScanContext context = methodContext();
        Expression condition = StaticJavaParser.parseExpression("value > 1");
        ReturnStmt returnStmt = StaticJavaParser.parseStatement("return value;").asReturnStmt();
        SwitchEntry switchEntry = StaticJavaParser.parseStatement("switch (value) { case 1 -> value++; }")
            .asSwitchStmt()
            .getEntry(0);

        assertThat(strategy.renderCondition(condition, context)).isEqualTo("COND:value > 1");
        assertThat(strategy.renderReturn(returnStmt, context)).isEqualTo("RETURN:return value;");
        assertThat(strategy.renderSwitchLabel(switchEntry, context)).isEqualTo("SWITCH:1");
        assertThat(traces)
            .containsExactly(
                "condition:COND:value > 1",
                "return:RETURN:return value;",
                "switch:SWITCH:1"
            );
    }

    @Test
    void appendTraceAppendsKindAndRenderedValue() {
        List<String> traces = new ArrayList<>();
        TracingConditionRenderingStrategy strategy = new TracingConditionRenderingStrategy(new StubStrategy(), traces);

        strategy.appendTrace("custom", "value");

        assertThat(traces).containsExactly("custom:value");
    }

    private static MethodScanContext methodContext() {
        MethodDeclaration method = StaticJavaParser.parse("""
            class Sample {
                boolean run(int value) {
                    return value > 0;
                }
            }
            """).findFirst(MethodDeclaration.class).orElseThrow();
        return new MethodScanContext(method, Map.of("value", 1), Set.of());
    }

    private static final class StubStrategy implements ConditionRenderingStrategy {
        @Override
        public String renderCondition(Expression condition, MethodScanContext context) {
            return "COND:" + condition;
        }

        @Override
        public String renderReturn(ReturnStmt returnStmt, MethodScanContext context) {
            return "RETURN:" + returnStmt;
        }

        @Override
        public String renderSwitchLabel(SwitchEntry entry, MethodScanContext context) {
            return "SWITCH:" + entry.getLabels().get(0);
        }
    }
}
