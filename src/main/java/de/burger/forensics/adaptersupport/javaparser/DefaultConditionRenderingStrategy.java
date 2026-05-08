package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default rendering strategy replicating the legacy scanner behaviour.
 */
public record DefaultConditionRenderingStrategy(
        InstanceFieldNormalizer instanceFieldNormalizer,
        StaticFieldQualifier staticFieldQualifier,
        TypeReferenceQualifier typeReferenceQualifier) implements ConditionRenderingStrategy {

    public DefaultConditionRenderingStrategy(InstanceFieldNormalizer instanceFieldNormalizer) {
        this(instanceFieldNormalizer, new StaticFieldQualifier());
    }

    public DefaultConditionRenderingStrategy(InstanceFieldNormalizer instanceFieldNormalizer,
                                             StaticFieldQualifier staticFieldQualifier) {
        this(
                instanceFieldNormalizer,
                staticFieldQualifier,
                new DefaultTypeReferenceQualifier(instanceFieldNormalizer, staticFieldQualifier));
    }

    public DefaultConditionRenderingStrategy {
        Objects.requireNonNull(instanceFieldNormalizer, "instanceFieldNormalizer");
        Objects.requireNonNull(staticFieldQualifier, "staticFieldQualifier");
        Objects.requireNonNull(typeReferenceQualifier, "typeReferenceQualifier");
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
        return typeReferenceQualifier.qualify(expression, context).expression();
    }
}
