package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.expr.Expression;

/**
 * Qualifies source-level condition expressions using the scanner context.
 */
public interface TypeReferenceQualifier {

    QualifiedExpression qualify(Expression expression, MethodScanContext context);
}
