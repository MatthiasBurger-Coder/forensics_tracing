package de.burger.forensics.adaptersupport.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;

/**
 * Strategy used to render expressions used by scan events.
 */
public interface ConditionRenderingStrategy {

    String renderCondition(Expression condition, MethodScanContext context);

    String renderReturn(ReturnStmt returnStmt, MethodScanContext context);

    String renderSwitchLabel(SwitchEntry entry, MethodScanContext context);
}
