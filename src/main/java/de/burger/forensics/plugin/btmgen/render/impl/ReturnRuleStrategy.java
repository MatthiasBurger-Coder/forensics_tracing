package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class ReturnRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "RETURN"; }

    @Override public String render(RuleParams p) {
        String returnValue = returnValueExpression(p.returnType());
        return """
            RULE %s : return %s
            CLASS %s
            METHOD %s
            HELPER %s
            %s
            IF true
            DO
                onExit(%s.class, "%s", %s );
            ENDRULE
            """.formatted(
                safeId(p.id()), ruleTarget(p),
                p.className(),
                methodSig(p.methodName(), p.methodDesc()),
                p.helperFqn(),
                atExit(),
                p.className(), p.methodName(), returnValue
        );
    }

    private static String returnValueExpression(String returnType) {
        return isVoid(returnType) ? "null" : "$!";
    }

    private static boolean isVoid(String returnType) {
        return returnType != null && "void".equalsIgnoreCase(returnType.trim());
    }
}
