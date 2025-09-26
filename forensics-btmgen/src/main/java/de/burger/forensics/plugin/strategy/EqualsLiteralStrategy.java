package de.burger.forensics.plugin.strategy;

/** Renders: leftExpr == literal (both strings are assumed already escaped). */
public final class EqualsLiteralStrategy implements ConditionStrategy {
    private final String leftExpr;
    private final String literal;

    public EqualsLiteralStrategy(String leftExpr, String literal) {
        this.leftExpr = leftExpr;
        this.literal = literal;
    }

    @Override
    public String toBytemanIf() {
        return leftExpr + " == " + literal;
    }
}
