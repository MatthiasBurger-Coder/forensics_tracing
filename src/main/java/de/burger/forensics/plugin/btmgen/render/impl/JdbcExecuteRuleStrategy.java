package de.burger.forensics.plugin.btmgen.render.impl;

import de.burger.forensics.plugin.btmgen.render.api.RuleParams;
import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;
import de.burger.forensics.plugin.btmgen.render.spi.AbstractBytemanStrategy;

public final class JdbcExecuteRuleStrategy extends AbstractBytemanStrategy implements RuleRenderStrategy {
    @Override public String id() { return "JDBC_EXECUTE"; }

    @Override public String render(RuleParams p) {
        String target  = "java.sql.Statement";
        String methods = "(execute|executeQuery|executeUpdate|executeLargeUpdate|executeBatch)";
        String hint = (p.sqlHint() == null || p.sqlHint().isBlank()) ? "" : " :: " + esc(trim(p.sqlHint(), 80));
        String id = safeId(p.id());
        return """
            RULE %s-begin : jdbc io begin
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT ENTRY
            IF true
            DO
                ioBegin("JDBC", "%s#%s%s");
            ENDRULE

            RULE %s-end : jdbc io end
            CLASS %s
            METHOD %s(..)
            HELPER %s
            AT EXIT
            IF true
            DO
                ioEnd("JDBC", "%s#%s%s");
            ENDRULE
            """.formatted(
                id, target, methods, p.helperFqn(), target, methods, hint,
                id, target, methods, p.helperFqn(), target, methods, hint
        );
    }

    private static String trim(String s, int max) {
        return (s.length() <= max) ? s : s.substring(0, Math.max(0, max - 3)) + "...";
    }
}
