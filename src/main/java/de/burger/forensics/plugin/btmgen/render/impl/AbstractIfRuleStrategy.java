package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

abstract class AbstractIfRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {

    @Override
    public final String render(RuleParams params) {
        String ruleId = safeId(params.id());
        int sourceLine = requireSourceLine(params, id());
        String condition = sanitizeCondition(params.condition());
        condition = resolveClassPlaceholder(params.className(), condition);
        condition = qualifyStaticNullCheck(params.className(), condition);
        String booleanExpr = booleanExpression(condition);
        String guardedExpr = guardedCondition(ruleId, booleanExpr, booleanExpr);
        return """
            RULE %s : %s %s#%s
            CLASS %s
            METHOD %s
            HELPER %s
            AT LINE %d
            IF %s
            DO
                onBranch(%s.class, "%s", "%s");
            ENDRULE
            """.formatted(
                ruleId, branchLabel(), or(params.displayName(), params.className()), params.methodName(),
                params.className(),
                methodSig(params.methodName(), params.methodDesc()),
                params.helperFqn(),
                sourceLine,
                guardedExpr,
                params.className(), params.methodName(), id()
        );
    }

    protected abstract String branchLabel();

    protected abstract String booleanExpression(String condition);
}
