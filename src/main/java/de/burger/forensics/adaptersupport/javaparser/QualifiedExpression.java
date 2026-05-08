package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.expr.Expression;

import java.util.Objects;

/**
 * Result of source-expression qualification before condition rendering.
 */
public record QualifiedExpression(Expression expression) {

    public QualifiedExpression {
        Objects.requireNonNull(expression, "expression");
    }
}
