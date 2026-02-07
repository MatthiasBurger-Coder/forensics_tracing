package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default rendering strategy replicating the legacy scanner behaviour.
 */
public record DefaultConditionRenderingStrategy(
        InstanceFieldNormalizer instanceFieldNormalizer,
        StaticFieldQualifier staticFieldQualifier) implements ConditionRenderingStrategy {

    public DefaultConditionRenderingStrategy(InstanceFieldNormalizer instanceFieldNormalizer) {
        this(instanceFieldNormalizer, new StaticFieldQualifier());
    }

    @Override
    public String renderCondition(Expression condition, MethodScanContext context) {
        Expression sanitized = sanitizeExpression(condition, context);
        String rendered = sanitized.toString();
        // rewrite static MDC access to helper method mdc(...)
        rendered = rendered.replace("MDC.get(", "mdc(");
        rendered = rendered.replace("org.slf4j.MDC.get(", "mdc(");
        return rendered;
    }

    @Override
    public String renderReturn(ReturnStmt returnStmt, MethodScanContext context) {
        ReturnStmt clone = returnStmt.clone();
        clone.getExpression().ifPresent(expr -> clone.setExpression(sanitizeExpression(expr, context)));
        return clone.toString();
    }

    @Override
    public String renderSwitchLabel(SwitchEntry entry, MethodScanContext context) {
        SwitchEntry clone = entry.clone();
        for (int i = 0; i < clone.getLabels().size(); i++) {
            Expression label = clone.getLabels().get(i);
            clone.getLabels().set(i, sanitizeExpression(label, context));
        }
        if (clone.getLabels().isEmpty()) {
            return "default";
        }
        return clone.getLabels().stream()
                .map(Node::toString)
                .map(String::trim)
                .collect(Collectors.joining(" | "));
    }

    Expression sanitizeExpression(Expression expression, MethodScanContext context) {
        Set<Range> instanceFieldRanges = instanceFieldNormalizer.identifyInstanceFieldRanges(expression, context.localVariables());
        Set<Range> staticFieldRanges = staticFieldQualifier.identifyStaticFieldRanges(expression, context.localVariables());
        Expression clone = expression.clone();
        clone.walk(NameExpr.class, name -> {
            Integer index = context.parameterIndex(name.getNameAsString());
            if (index != null) {
                name.setName("$" + index);
                return;
            }
            if (context.isLocalVariable(name.getNameAsString())) {
                name.setName("$" + name.getNameAsString());
                return;
            }
            if (staticFieldQualifier.qualifyStaticFieldAccess(name, staticFieldRanges)) {
                return;
            }
            instanceFieldNormalizer.promoteInstanceFieldAccess(name, instanceFieldRanges);
        });
        return clone;
    }
}
